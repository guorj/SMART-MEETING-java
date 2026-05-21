/**
 * Feishu Bitable MCP Server
 *
 * 提供飞书多维表格读取工具，供 OpenClaw Gateway 上的 AI Agent 通过 MCP 协议调用。
 *
 * 工具列表：
 *   - read_bitable_rows: 读取飞书多维表格记录列表
 *   - list_bitable_fields: 获取字段列表（列名与类型）
 *
 * 传输方式：stdio（与 Gateway 同机部署，延迟最低）
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

// ============================================================
// 飞书 API 辅助
// ============================================================

const FEISHU_BASE_URL = "https://open.feishu.cn";

interface TokenCache {
  token: string;
  expireAt: number; // ms timestamp
}

let tokenCache: TokenCache | null = null;

async function getTenantAccessToken(): Promise<string> {
  // 缓存提前 5 分钟过期
  if (tokenCache && Date.now() < tokenCache.expireAt - 5 * 60 * 1000) {
    return tokenCache.token;
  }

  const appId = process.env.FEISHU_APP_ID;
  const appSecret = process.env.FEISHU_APP_SECRET;

  if (!appId || !appSecret) {
    throw new Error("FEISHU_APP_ID and FEISHU_APP_SECRET environment variables are required");
  }

  const resp = await fetch(`${FEISHU_BASE_URL}/open-apis/auth/v3/tenant_access_token/internal`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ app_id: appId, app_secret: appSecret }),
  });

  const data = await resp.json() as any;
  if (data.code !== 0) {
    throw new Error(`Feishu auth failed: ${data.code} ${data.msg}`);
  }

  tokenCache = {
    token: data.tenant_access_token,
    expireAt: Date.now() + (data.expire || 7200) * 1000,
  };

  return tokenCache.token;
}

async function feishuGet(path: string, params?: Record<string, string>): Promise<any> {
  const token = await getTenantAccessToken();
  const url = new URL(`${FEISHU_BASE_URL}${path}`);
  if (params) {
    Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
  }

  const resp = await fetch(url.toString(), {
    headers: { Authorization: `Bearer ${token}` },
  });

  const data = await resp.json() as any;
  if (data.code !== 0) {
    throw new Error(`Feishu API error: ${data.code} ${data.msg}`);
  }
  return data.data;
}

// ============================================================
// MCP Server 定义
// ============================================================

const server = new McpServer({
  name: "feishu-bitable",
  version: "1.0.0",
});

server.tool(
  "read_bitable_rows",
  "读取飞书多维表格记录列表。传入 app_token、table_id 即可获取记录，支持分页。",
  {
    app_token: z.string().describe("多维表格 app_token（从 URL 中提取）"),
    table_id: z.string().describe("数据表 table_id"),
    view_id: z.string().optional().describe("视图 ID（可选，不传则使用默认视图）"),
    page_size: z.number().optional().default(100).describe("每页记录数，默认 100，最大 500"),
    page_token: z.string().optional().describe("分页 token（首次不传，后续传入返回的 page_token）"),
  },
  async ({ app_token, table_id, view_id, page_size, page_token }) => {
    try {
      const params: Record<string, string> = {
        page_size: String(page_size || 100),
      };
      if (view_id) params.view_id = view_id;
      if (page_token) params.page_token = page_token;

      const data = await feishuGet(
        `/open-apis/bitable/v1/apps/${app_token}/tables/${table_id}/records`,
        params,
      );

      return {
        content: [
          {
            type: "text" as const,
            text: JSON.stringify(data, null, 2),
          },
        ],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text" as const, text: JSON.stringify({ isError: true, message: error.message }) }],
        isError: true,
      };
    }
  },
);

server.tool(
  "list_bitable_fields",
  "获取飞书多维表格字段列表（列名与类型），用于了解表格结构。",
  {
    app_token: z.string().describe("多维表格 app_token"),
    table_id: z.string().describe("数据表 table_id"),
  },
  async ({ app_token, table_id }) => {
    try {
      const data = await feishuGet(
        `/open-apis/bitable/v1/apps/${app_token}/tables/${table_id}/fields`,
      );

      return {
        content: [
          {
            type: "text" as const,
            text: JSON.stringify(data, null, 2),
          },
        ],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text" as const, text: JSON.stringify({ isError: true, message: error.message }) }],
        isError: true,
      };
    }
  },
);

// ============================================================
// 启动
// ============================================================

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // stderr 仅用于调试日志，不影响 MCP stdio 通信
  console.error("feishu-bitable MCP Server running on stdio");
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});

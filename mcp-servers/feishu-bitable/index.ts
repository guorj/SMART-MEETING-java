import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const FEISHU_APP_ID = process.env.FEISHU_APP_ID || "";
const FEISHU_APP_SECRET = process.env.FEISHU_APP_SECRET || "";

const server = new McpServer({
  name: "feishu-bitable",
  version: "1.0.0",
});

let accessToken = "";
let tokenExpireAt = 0;

async function getAccessToken(): Promise<string> {
  if (accessToken && Date.now() < tokenExpireAt) {
    return accessToken;
  }
  const resp = await fetch("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ app_id: FEISHU_APP_ID, app_secret: FEISHU_APP_SECRET }),
  });
  const data = await resp.json() as any;
  if (data.code !== 0) {
    throw new Error(`飞书 tenant_access_token 获取失败: code=${data.code}, msg=${data.msg}`);
  }
  accessToken = data.tenant_access_token;
  tokenExpireAt = Date.now() + (data.expire - 300) * 1000;
  return accessToken;
}

async function feishuApi(path: string): Promise<any> {
  const token = await getAccessToken();
  const resp = await fetch(`https://open.feishu.cn${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return resp.json();
}

server.tool(
  "read_bitable_rows",
  "读取飞书多维表格的记录列表，返回 JSON 行数据",
  {
    app_token: z.string().describe("多维表格 app_token，从 URL /base/ 后截取"),
    table_id: z.string().describe("表格 table_id，从 URL table= 后截取"),
    view_id: z.string().optional().describe("视图 view_id，从 URL view= 后截取"),
    page_size: z.number().optional().default(100).describe("每页记录数，最大 500"),
    page_token: z.string().optional().describe("分页游标，首次为空"),
  },
  async ({ app_token, table_id, view_id, page_size, page_token }) => {
    try {
      const params = new URLSearchParams({
        page_size: String(page_size),
        ...(view_id ? { view_id } : {}),
        ...(page_token ? { page_token } : {}),
      });
      const data = await feishuApi(
        `/open-apis/bitable/v1/apps/${app_token}/tables/${table_id}/records?${params}`
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: `读取飞书多维表格失败: ${e.message}` }],
        isError: true,
      };
    }
  }
);

server.tool(
  "list_bitable_fields",
  "获取飞书多维表格的字段列表（列名与类型）",
  {
    app_token: z.string().describe("多维表格 app_token"),
    table_id: z.string().describe("表格 table_id"),
  },
  async ({ app_token, table_id }) => {
    try {
      const data = await feishuApi(
        `/open-apis/bitable/v1/apps/${app_token}/tables/${table_id}/fields`
      );
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }],
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: `获取飞书多维表格字段失败: ${e.message}` }],
        isError: true,
      };
    }
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);
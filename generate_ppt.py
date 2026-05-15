#!/usr/bin/env python3
"""
智能会议系统 - 项目汇报PPT v3 (严格按MD文档补全内容)
"""
import sys, os
sys.path.insert(0, '/mnt/d/openclaw/workspace/skills/ppt-generator/scripts')
from pptx import Presentation
from pptx.util import Pt, Cm
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR_TYPE
from pptx.oxml.ns import qn
from helpers import *

# ========== 配色 ==========
C_DARK_BG    = RGBColor(0x0D, 0x14, 0x22)
C_DEEP_BLUE  = RGBColor(0x2C, 0x3E, 0x6B)
C_ACCENT_BLUE= RGBColor(0x16, 0x5D, 0xFF)
C_LIGHT_BLUE = RGBColor(0xE8, 0xF0, 0xFE)
C_GREEN_DONE = RGBColor(0x27, 0xAE, 0x60)
C_YELLOW_DEV = RGBColor(0xF3, 0x9C, 0x12)
C_GRAY_TODO  = RGBColor(0xA0, 0xA0, 0xA0)
C_BG_CARD    = RGBColor(0xFA, 0xFB, 0xFC)
C_BORDER     = RGBColor(0xE2, 0xE8, 0xF0)
C_TEXT_DARK  = RGBColor(0x1E, 0x29, 0x3B)
C_PURPLE     = RGBColor(0x8E, 0x44, 0xAD)
C_OVERLAY    = RGBColor(0x0D, 0x14, 0x22)
C_DARK_TABLE = RGBColor(0x2C, 0x3E, 0x6B)

TEMPLATE = "/mnt/d/openclaw/workspace-clone/finalData/PPT模板.pptx"
OUTPUT = "/mnt/d/openclaw/workspace-clone/projects/smart-meeting-java/智能会议系统-项目汇报PPT_v3.pptx"
IMG_DIR = "/mnt/d/openclaw/workspace-clone/projects/smart-meeting-java/images"

# ========== 工具函数 ==========
def _set_transparency(shape, alpha):
    spPr = shape._element.find(qn('p:spPr'))
    if spPr is None: spPr = shape._element.find(qn('a:spPr'))
    if spPr is None: return
    solidFill = spPr.find(qn('a:solidFill'))
    if solidFill is None: return
    srgb = solidFill.find(qn('a:srgbClr'))
    if srgb is None: return
    alpha_elem = srgb.makeelement(qn('a:alpha'), {'val': str(int(alpha * 1000))})
    srgb.append(alpha_elem)

def add_rrect(slide, l, t, w, h, fill, border=None, alpha=None):
    s = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Cm(l), Cm(t), Cm(w), Cm(h))
    s.fill.solid(); s.fill.fore_color.rgb = fill
    if alpha: _set_transparency(s, alpha)
    if border: s.line.color.rgb = border; s.line.width = Pt(0.5)
    else: s.line.fill.background()
    s.adjustments[0] = 0.08
    return s

def add_rect(slide, l, t, w, h, fill, border=None, alpha=None):
    s = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Cm(l), Cm(t), Cm(w), Cm(h))
    s.fill.solid(); s.fill.fore_color.rgb = fill
    if alpha: _set_transparency(s, alpha)
    if border: s.line.color.rgb = border; s.line.width = Pt(0.5)
    else: s.line.fill.background()
    return s

def add_circ(slide, l, t, d, fill):
    s = slide.shapes.add_shape(MSO_SHAPE.OVAL, Cm(l), Cm(t), Cm(d), Cm(d))
    s.fill.solid(); s.fill.fore_color.rgb = fill; s.line.fill.background()
    return s

def add_tb(slide, l, t, w, h, text, size=12, bold=False, color=C_BODY, align=PP_ALIGN.LEFT):
    tb = slide.shapes.add_textbox(Cm(l), Cm(t), Cm(w), Cm(h))
    tf = tb.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; p.alignment = align
    r = p.add_run(); r.text = str(text)
    r.font.name = FONT; r.font.size = Pt(size); r.font.bold = bold; r.font.color.rgb = color
    return tf

def add_mbox(slide, l, t, w, h, lines, size=11, color=C_BODY, lh=1.5):
    tb = slide.shapes.add_textbox(Cm(l), Cm(t), Cm(w), Cm(h))
    tf = tb.text_frame; tf.word_wrap = True
    for i, ln in enumerate(lines):
        text, bold, clr = ln[0], ln[1] if len(ln)>1 else False, ln[2] if len(ln)>2 else color
        p = tf.add_paragraph() if i>0 else tf.paragraphs[0]
        p.space_after = Pt(2); p.line_spacing = Pt(size*lh)
        r = p.add_run(); r.text = str(text)
        r.font.name = FONT; r.font.size = Pt(size); r.font.bold = bold; r.font.color.rgb = clr
    return tf

def add_line(slide, x1, y1, x2, y2, color=C_GRAY, w=1.5):
    c = slide.shapes.add_connector(MSO_CONNECTOR_TYPE.STRAIGHT, Cm(x1), Cm(y1), Cm(x2), Cm(y2))
    c.line.color.rgb = color; c.line.width = Pt(w)
    return c

def set_stext(shape, text, size=11, bold=False, color=C_BODY, align=PP_ALIGN.CENTER):
    tf = shape.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; p.alignment = align
    r = p.add_run(); r.text = str(text)
    r.font.name = FONT; r.font.size = Pt(size); r.font.bold = bold; r.font.color.rgb = color

def add_bg(slide, img, alpha=0.55):
    img_path = os.path.join(IMG_DIR, img)
    if os.path.exists(img_path):
        slide.shapes.add_picture(img_path, Cm(0), Cm(0), Cm(33.8), Cm(19.0))
    add_rect(slide, 0, 0, 33.8, 19.0, C_OVERLAY, alpha=alpha)

def add_pic(slide, l, t, w, h, img):
    img_path = os.path.join(IMG_DIR, img)
    if os.path.exists(img_path):
        return slide.shapes.add_picture(img_path, Cm(l), Cm(t), Cm(w), Cm(h))
    return None

def page_header(slide, num, title, subtitle):
    add_rect(slide, 0, 0, 33.8, 3.2, C_LIGHT_BLUE, alpha=0.9)
    add_tb(slide, 2, 0.5, 20, 1, f"{num:02d}  {title}", size=22, bold=True, color=C_DEEP_BLUE)
    add_tb(slide, 2, 1.7, 28, 0.7, subtitle, size=10, color=C_TEXT_DARK)


def main():
    prs = init_prs(TEMPLATE)
    prs.slide_width = Cm(33.8)
    prs.slide_height = Cm(19.0)

    # ============================================================
    # PAGE 1: 封面 — 深色背景 + 环形进度 + 项目名
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-tech-blue.jpg", alpha=0.55)
    add_circ(slide, 28, -3, 10, RGBColor(0x16, 0x5D, 0xFF))
    add_circ(slide, -4, 13, 8, RGBColor(0x1A, 0x3D, 0x9A))
    add_circ(slide, 29, 7, 5, RGBColor(0x2C, 0x3E, 0x6B))
    tag = add_rrect(slide, 2.5, 3, 3.2, 0.8, C_ACCENT_BLUE)
    set_stext(tag, "项目进展汇报", size=10, bold=True, color=C_WHITE)
    add_tb(slide, 2.5, 4.5, 20, 2.5, "智能会议系统", size=48, bold=True, color=C_WHITE)
    add_tb(slide, 2.5, 7.5, 22, 1.2,
           "飞书为入口 / AI主持人为中枢 / 全链路无人值守",
           size=15, color=RGBColor(0xA0, 0xB4, 0xD0))
    add_line(slide, 2.5, 8.9, 7, 8.9, C_ACCENT_BLUE, 3)
    add_circ(slide, 22.5, 5, 8.5, RGBColor(0x1A, 0x2D, 0x5A))
    add_circ(slide, 23.8, 6.3, 6, C_DARK_BG)
    add_tb(slide, 24.3, 7.8, 5, 1.5, "20%", size=36, bold=True, color=C_WHITE, align=PP_ALIGN.CENTER)
    add_tb(slide, 23.3, 9.5, 7, 0.8, "整体进度", size=11, color=RGBColor(0xA0, 0xB4, 0xD0), align=PP_ALIGN.CENTER)
    add_rect(slide, 0, 16.8, 33.8, 2.2, RGBColor(0x0A, 0x0F, 0x1A), alpha=0.6)
    add_tb(slide, 2.5, 17.2, 12, 0.6, "汇报人：开发团队  |  2026年5月", size=10, color=C_GRAY)
    add_tb(slide, 18, 17.2, 13, 0.6, "版本 v1.0  |  机密", size=10, color=C_GRAY, align=PP_ALIGN.RIGHT)

    # ============================================================
    # PAGE 2: 目录
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-abstract.jpg", alpha=0.7)
    add_rect(slide, 0, 0, 10, 19.0, C_DARK_BG, alpha=0.85)
    add_tb(slide, 2, 3.5, 6, 2, "CONTENTS", size=26, bold=True, color=C_WHITE)
    add_tb(slide, 2, 5.5, 6, 1, "汇报目录", size=13, color=C_GRAY)
    items = [
        ("01", "用户体验旅程", "一场会议的完整体验 —— 核心主图"),
        ("02", "功能进度矩阵", "18个功能点开发状态总览"),
        ("03", "数据飞轮", "永不停歇的闭环设计"),
        ("04", "传统 vs 智能", "AI 做掉 90% 运营工作"),
        ("05", "后续计划", "2个月/6个月里程碑"),
    ]
    for i, (num, title, desc) in enumerate(items):
        y = 3.0 + i * 2.6
        add_circ(slide, 11.5, y, 1.4, C_ACCENT_BLUE if i==0 else C_BG_CARD)
        add_tb(slide, 11.5, y+0.25, 1.4, 1, num, size=16, bold=True,
               color=C_WHITE if i==0 else C_TITLE, align=PP_ALIGN.CENTER)
        add_tb(slide, 13.5, y, 10, 0.7, title, size=15, bold=True, color=C_TEXT_DARK)
        add_tb(slide, 13.5, y+0.8, 10, 0.5, desc, size=9, color=C_GRAY)
        if i < 4: add_line(slide, 13.5, y+2.0, 28, y+2.0, C_BORDER, 0.3)

    # ============================================================
    # PAGE 3: 用户体验旅程图 (核心主图) — 严格按MD文档的表格布局
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-meeting.jpg", alpha=0.85)
    page_header(slide, 1, "用户体验旅程",
                "一场综合管理会的完整体验：人只需到场、发言、决策，其余全部由系统自动完成")

    # 精确按MD文档构建矩阵
    stages = ["会前24h", "会前10min", "会中60min", "会后5min", "会后两周", "下次会议"]
    human_actions = [
        "无（系统全自动）",
        "点签到\n点进入会议",
        "发言\n决策",
        "无（系统全自动）",
        "点&lsquo;完成&rsquo;\n或&lsquo;延期&rsquo;",
        "无（系统全自动）"
    ]
    system_actions = [
        ("1. 检测周期会议触发\n"
         "2. 事项从待办+议题+告知归集\n"
         "3. 飞书群弹出邀约卡片\n"
         "4. 参会人确认/请假\n"
         "5. 关键决策人请假→通知组织人\n"
         "6. 日历API创建+会议室预约"),
        ("1. 飞书群弹出盘点卡片\n"
         "2. 显示:谁已签到/声纹就绪\n"
         "   谁上次待办延期\n"
         "3. 点签到→声纹验证\n"
         "4. 点进入会议"),
        ("1. AI主持人自动开场\n"
         "2. 检点点名，统计人数\n"
         "3. 通报上次待办完成情况\n"
         "4. 切换到议题(计时开启)\n"
         "5. 实时ASR转写+声纹分离\n"
         "6. 剩余1分钟→提醒\n"
         "7. 到时→自动切下一议题"),
        ("1. 发起人点结束→AI播报\n"
         "2. 离线校正\n"
         "3. 声纹识别补全\n"
         "4. LLM生成结构化纪要\n"
         "5. 创建飞书文档→写入\n"
         "6. 飞书群弹出纪要链接"),
        ("1. 每日飞书提醒待办\n"
         "2. 到期前24h催办\n"
         "3. 截止日过期→标DELAYED\n"
         "4. 直属上级收到延期通知"),
        ("1. 下次会议自动创建\n"
         "2. 邀约携带上次纪要+待办\n"
         "3. AI通报待办完成/延期\n"
         "4. 循环重启")
    ]
    statuses = ["未开始", "未开始", "进行中", "进行中", "未开始", "未开始"]
    status_colors = [C_GRAY_TODO, C_GRAY_TODO, C_YELLOW_DEV, C_YELLOW_DEV, C_GRAY_TODO, C_GRAY_TODO]
    progress_vals = [0.0, 0.0, 0.60, 0.35, 0.0, 0.0]  # per-stage completion

    # === Row 0: 时间轴 ===
    y0 = 3.8
    add_line(slide, 1.5, y0+0.5, 32, y0+0.5, C_TEXT_DARK, 1.5)
    # 箭头
    arrow = slide.shapes.add_shape(MSO_SHAPE.ISOSCELES_TRIANGLE, Cm(31.5), Cm(y0+0.2), Cm(0.8), Cm(0.6))
    arrow.fill.solid(); arrow.fill.fore_color.rgb = C_TEXT_DARK; arrow.line.fill.background()
    arrow.rotation = 90  # point right

    col_w = 5.0; gap = 0.2; start_x = 1.5
    for i, s in enumerate(stages):
        x = start_x + i*(col_w+gap)
        add_tb(slide, x, y0, col_w, 0.5, s, size=10, bold=True, color=C_TEXT_DARK, align=PP_ALIGN.CENTER)

    # === Row 1: 人的动作 ===
    y1 = y0 + 0.7
    row_label1 = add_rrect(slide, 0.2, y1, 1.2, 1.3, C_DARK_BG)
    set_stext(row_label1, "人的\n动作", size=8, bold=True, color=C_WHITE)
    for i, ha in enumerate(human_actions):
        x = start_x + i*(col_w+gap)
        bg = C_BG_CARD
        add_rrect(slide, x, y1, col_w, 2.0, bg, C_BORDER)
        # Bold if "无" for emphasis
        is_bold = "无" in ha
        add_tb(slide, x+0.3, y1+0.2, col_w-0.6, 1.6, ha, size=8, bold=is_bold, color=C_TEXT_DARK, align=PP_ALIGN.CENTER if "\n" not in ha else PP_ALIGN.LEFT)

    # === Row 2: 系统动作 ===
    y2 = y1 + 2.3
    row_label2 = add_rrect(slide, 0.2, y2, 1.2, 5.5, C_ACCENT_BLUE)
    set_stext(row_label2, "系统\n动作", size=8, bold=True, color=C_WHITE)
    for i, sa in enumerate(system_actions):
        x = start_x + i*(col_w+gap)
        add_rrect(slide, x, y2, col_w, 5.5, C_WHITE, C_BORDER)
        add_tb(slide, x+0.2, y2+0.2, col_w-0.4, 5.1, sa, size=6.5, color=C_TEXT_DARK, align=PP_ALIGN.LEFT)

    # === Row 3: 进度条 ===
    y3 = y2 + 5.8
    row_label3 = add_rrect(slide, 0.2, y3, 1.2, 0.6, C_DARK_BG)
    set_stext(row_label3, "进度", size=8, bold=True, color=C_WHITE)
    for i, (pv, sc) in enumerate(zip(progress_vals, status_colors)):
        x = start_x + i*(col_w+gap)
        # bg
        add_rrect(slide, x, y3, col_w, 0.6, C_BORDER)
        # fill
        if pv > 0: add_rrect(slide, x, y3, col_w*pv, 0.6, sc)
        # status text
        add_tb(slide, x, y3, col_w, 0.6, statuses[i], size=7, bold=True, color=sc, align=PP_ALIGN.CENTER)

    # === 底部汇总 ===
    bar_y = 15.5
    add_tb(slide, 1.5, bar_y-0.5, 4, 0.4, "整体进度", size=8, bold=True, color=C_GRAY)
    add_rrect(slide, 1.5, bar_y, 29, 0.4, C_BORDER)
    # done 8%, dev 12%, todo 80%
    add_rrect(slide, 1.5, bar_y, 29*0.08, 0.4, C_GREEN_DONE)
    add_rrect(slide, 1.5+29*0.08, bar_y, 29*0.12, 0.4, C_YELLOW_DEV)
    add_rrect(slide, 1.5+29*0.20, bar_y, 29*0.80, 0.4, C_GRAY_TODO)

    # 图例
    ly = bar_y + 0.8
    add_circ(slide, 1.5, ly, 0.3, C_GREEN_DONE)
    add_tb(slide, 2.0, ly, 2, 0.3, "已完成", size=7, color=C_TEXT_DARK)
    add_circ(slide, 5, ly, 0.3, C_YELLOW_DEV)
    add_tb(slide, 5.5, ly, 3, 0.3, "进行中/开发中", size=7, color=C_TEXT_DARK)
    add_circ(slide, 9, ly, 0.3, C_GRAY_TODO)
    add_tb(slide, 9.5, ly, 3, 0.3, "未开始", size=7, color=C_TEXT_DARK)

    add_key_msg(slide, "人的操作集中在&ldquo;发言决策&rdquo;和&ldquo;点完成/延期&rdquo;，系统覆盖 90% 的会议运营")
    add_source(slide, "基于MD文档 v1.0 | 会中60% / 会后35% / 会前0% / 跟踪0% | 整体20%")

    # ============================================================
    # PAGE 4: 功能进度矩阵 — 完整18项表格
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-abstract.jpg", alpha=0.88)
    page_header(slide, 2, "功能进度矩阵",
                "18个功能点按6个阶段拆解，一页看清&ldquo;做到哪了&rdquo;")

    table_data = [
        ["阶段", "功能点", "状态", "完成度"],
        ["会前24h", "自动创建会议", "未开始", "0%"],
        ["会前24h", "飞书邀约卡片", "未开始", "0%"],
        ["会前24h", "参会确认+关键人判断", "未开始", "0%"],
        ["会前24h", "日历+会议室预约", "未开始", "0%"],
        ["会前10min", "盘点卡片+签到", "未开始", "0%"],
        ["会中", "AI自动开场", "已完成", "100%"],
        ["会中", "会议检点点名", "已完成", "100%"],
        ["会中", "上次待办通报", "开发中", "50%"],
        ["会中", "议题切换", "已完成", "100%"],
        ["会中", "实时ASR+声纹", "进行中", "60%"],
        ["会中", "议题时间提醒", "部分完成", "50%"],
        ["会中", "议题超时自动切换", "已完成", "100%"],
        ["会后", "结束播报", "已完成", "100%"],
        ["会后", "纪要生成+飞书文档", "进行中", "70%"],
        ["会后", "待办提取+飞书任务", "未开始", "0%"],
        ["会后两周", "催办提醒", "未开始", "0%"],
        ["会后两周", "延期上报", "未开始", "0%"],
        ["下次会议", "闭环通报", "未开始", "0%"],
    ]

    add_styled_table(slide, 2.0, 3.8, 29.8, 12.8, table_data, col_widths=[4.5, 10, 7.3, 8])
    tbl_obj = slide.shapes[-1].table
    # 调整表格样式：合并阶段列
    for ri in range(1, len(table_data)):
        cell_status = tbl_obj.cell(ri, 2)
        stxt = table_data[ri][2]
        for p in cell_status.text_frame.paragraphs:
            for r in p.runs:
                if "已完成" in stxt: r.font.color.rgb = C_GREEN_DONE
                elif "开发中" in stxt or "进行中" in stxt or "部分" in stxt: r.font.color.rgb = C_YELLOW_DEV
                else: r.font.color.rgb = C_GRAY_TODO
                r.font.bold = True
        # 完成度列也用颜色标注
        cell_prog = tbl_obj.cell(ri, 3)
        pct = table_data[ri][3]
        for p in cell_prog.text_frame.paragraphs:
            for r in p.runs:
                if pct == "100%": r.font.color.rgb = C_GREEN_DONE
                elif pct == "0%": r.font.color.rgb = C_GRAY_TODO
                else: r.font.color.rgb = C_YELLOW_DEV
                r.font.bold = True

    add_key_msg(slide, "整体进度20%。会中7/7项全部有进展，会前5项和会后跟踪3项均为0，是下一阶段重点攻坚方向")
    add_source(slide, "统计截至 2026年5月14日 | 已完成=7项 | 开发中=4项 | 未开始=7项")

    # ============================================================
    # PAGE 5: 数据飞轮 — 四节点 + 中心进度
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-dark-tech.jpg", alpha=0.75)
    page_header(slide, 3, "数据飞轮",
                "会议不再是孤立事件，而是永续循环的闭环——每次会议都对上次有交代")

    cx, cy = 16.9, 9.5
    add_line(slide, 4, cy, 29.8, cy, C_BORDER, 0.3)
    add_line(slide, cx, 5.5, cx, 13.5, C_BORDER, 0.3)

    nodes = [
        ("会议召开", "AI主持/实时转写\n声纹分离/计时/切换", "50%", C_GREEN_DONE, (cx-6.3, cy-3.8)),
        ("纪要生成\n待办提取", "LLM结构化纪要\n飞书文档创建\n待办匹配责任人", "35%", C_YELLOW_DEV, (cx+2.5, cy+1.2)),
        ("待办跟踪\n催办提醒", "每日飞书提醒\n到期催办\n延期上报上级", "0%", C_GRAY_TODO, (cx-1.3, cy+1.2)),
        ("下次会议\n闭环通报", "自动承接上次待办\n通报完成/延期\n循环重启", "0%", C_GRAY_TODO, (cx-10.3, cy+1.2)),
    ]
    for title, desc, prog, color, (nx, ny) in nodes:
        n = add_rrect(slide, nx, ny, 7.5, 2.8, C_WHITE, color)
        add_rrect(slide, nx, ny, 7.5, 0.12, color)
        add_tb(slide, nx+0.4, ny+0.3, 6.7, 0.8, title, size=10, bold=True, color=C_TEXT_DARK)
        pt = add_rrect(slide, nx+5.2, ny+0.3, 1.8, 0.5, color)
        set_stext(pt, prog, size=7, bold=True, color=C_WHITE)
        add_tb(slide, nx+0.4, ny+1.2, 6.7, 1.4, desc, size=7, color=C_GRAY)

    # 中心
    add_circ(slide, cx-2.8, cy-2.8, 5.6, C_ACCENT_BLUE)
    add_circ(slide, cx-1.8, cy-1.8, 3.6, C_WHITE)
    add_tb(slide, cx-1.8, cy-1.2, 3.6, 1.0, "20%", size=24, bold=True, color=C_ACCENT_BLUE, align=PP_ALIGN.CENTER)
    add_tb(slide, cx-1.8, cy+0.1, 3.6, 0.6, "整体进度", size=8, color=C_TEXT_DARK, align=PP_ALIGN.CENTER)

    for ax, ay, sym in [(cx-0.3, cy-5.3,"v"),(cx+5.8,cy,">"),(cx-0.3,cy+4.2,"v"),(cx-6.2,cy,"<")]:
        add_tb(slide, ax, ay, 0.8, 0.6, sym, size=14, bold=True, color=C_ACCENT_BLUE, align=PP_ALIGN.CENTER)

    add_key_msg(slide, "飞轮完成20%。&ldquo;会议召开&rdquo;和&ldquo;纪要生成&rdquo;是核心引擎，会前+跟踪是缺齿")
    add_source(slide, "设计理念：每次会议都是下次会议的前置输入 | 闭环不是一次性的，而是永续循环")

    # ============================================================
    # PAGE 6: 传统 vs 智能 对比矩阵
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-abstract.jpg", alpha=0.88)
    page_header(slide, 4, "传统 vs 智能",
                "5个维度对比——AI做掉了90%的会议运营工作，人的角色从&ldquo;操作者&rdquo;变为&ldquo;决策者&rdquo;")

    lx = 2.5; cw = 13.8; rx = lx + cw + 1.5
    add_tb(slide, lx, 3.8, cw, 0.7, "传统会议", size=15, bold=True, color=C_GRAY, align=PP_ALIGN.CENTER)
    add_tb(slide, rx, 3.8, cw, 0.7, "智能会议", size=15, bold=True, color=C_ACCENT_BLUE, align=PP_ALIGN.CENTER)

    comps = [
        ("会前准备", "人工发邀约、手动预约会议室", "系统自动创建会议+邀约卡片\n参会确认+关键人判断+日历预约", C_GRAY_TODO),
        ("会中主持", "人工主持、人工计时、\n人工记录会议内容", "AI自动开场+主持+点名\n实时ASR转写+声纹分离\n计时提醒+超时自动切换议题", C_GREEN_DONE),
        ("会后整理", "人工整理纪要、\n手动分发给参会人", "离线校正+LLM生成结构化纪要\n自动创建飞书文档+群内推送", C_YELLOW_DEV),
        ("待办跟踪", "靠人记忆、靠人催办\n容易遗漏和遗忘", "自动从纪要提取待办+匹配责任人\n每日飞书提醒+到期催办+延期上报", C_GRAY_TODO),
        ("会议闭环", "开完就散，下次从头来\n上次决议无人跟进", "每次会议承接上次待办\n自动通报完成率+延期情况\n永不遗忘、永续循环", C_GRAY_TODO),
    ]

    for i, (dim, trad, smart, clr) in enumerate(comps):
        y = 5.0 + i*2.5
        add_tb(slide, 0.3, y+0.5, 2.5, 0.7, dim, size=9, bold=True, color=C_TEXT_DARK, align=PP_ALIGN.RIGHT)
        # 传统
        add_rrect(slide, lx, y, cw, 2.0, C_BG_CARD, C_BORDER)
        add_tb(slide, lx+0.5, y+0.2, cw-1, 1.6, trad, size=9, color=C_GRAY)
        # 智能
        add_rrect(slide, rx, y, cw, 2.0, C_WHITE, clr)
        add_rect(slide, rx, y, 0.15, 2.0, clr)
        add_tb(slide, rx+0.6, y+0.2, cw-1.2, 1.6, smart, size=8.5, color=C_TEXT_DARK)
        add_circ(slide, rx+cw-0.7, y+0.6, 0.4, clr)

    add_key_msg(slide, "用户从&ldquo;操作者&rdquo;变成&ldquo;决策者&rdquo;：只需到场+发言+点&ldquo;完成/延期&rdquo;，其余全部由AI接管")
    add_source(slide, "[OK]=已完成(会中) | [~]=进行中(会后) | [ ]=未开始(会前+跟踪+闭环)")

    # ============================================================
    # PAGE 7: 后续计划 — 时间轴
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-abstract.jpg", alpha=0.88)
    page_header(slide, 5, "后续计划",
                "分两阶段推进：2个月内补齐核心缺陷，6个月内完成全链路无人值守闭环")

    ty = 5.2
    add_line(slide, 3, ty, 31, ty, C_ACCENT_BLUE, 2)

    # M1: 2026年6月
    add_circ(slide, 6, ty-0.4, 0.8, C_ACCENT_BLUE)
    add_tb(slide, 4.5, ty+0.8, 4, 0.5, "2026年6月", size=10, bold=True, color=C_ACCENT_BLUE, align=PP_ALIGN.CENTER)
    add_rrect(slide, 3, ty+1.6, 7, 5.8, C_BG_CARD, C_ACCENT_BLUE)
    add_mbox(slide, 3.4, ty+2.0, 6.2, 5.2, [
        ("第一阶段：核心闭环", True, C_TEXT_DARK),
        ("", False, C_BODY),
        ("[OK] 声纹识别补全", False, C_GREEN_DONE),
        ("  讯飞ISV声纹+会后说话人识别", False, C_GRAY),
        ("[OK] 待办提取+飞书任务创建", False, C_GREEN_DONE),
        ("  从纪要提取待办/匹配责任人", False, C_GRAY),
        ("[OK] 纪要生成优化", False, C_GREEN_DONE),
        ("  LLM结构化+飞书文档写入", False, C_GRAY),
        ("[OK] ASR准确率提升", False, C_GREEN_DONE),
        ("  转写优化+离线校正", False, C_GRAY),
        ("[OK] 全场景异常测试", False, C_GREEN_DONE),
        ("  正常+异常+性能全覆盖", False, C_GRAY),
        ("", False, C_BODY),
        ("会中+会后核心闭环完成", False, C_ACCENT_BLUE),
    ], size=7.5, color=C_TEXT_DARK, lh=1.3)

    # M2: 2026年10月
    add_circ(slide, 20, ty-0.4, 0.8, C_YELLOW_DEV)
    add_tb(slide, 18.5, ty+0.8, 4, 0.5, "2026年10月", size=10, bold=True, color=C_YELLOW_DEV, align=PP_ALIGN.CENTER)
    add_rrect(slide, 17, ty+1.6, 7, 5.8, C_BG_CARD, C_YELLOW_DEV)
    add_mbox(slide, 17.4, ty+2.0, 6.2, 5.2, [
        ("第二阶段：全链路闭环", True, C_TEXT_DARK),
        ("", False, C_BODY),
        ("[ ] 会前自动创建会议", False, C_TEXT_DARK),
        ("  周期检测+事项归集", False, C_GRAY),
        ("[ ] 飞书邀约+参会确认", False, C_TEXT_DARK),
        ("  卡片+确认/请假+关键人判断", False, C_GRAY),
        ("[ ] 日历+会议室预约", False, C_TEXT_DARK),
        ("  飞书日历API+会议室资源", False, C_GRAY),
        ("[ ] 待办催办+延期上报", False, C_TEXT_DARK),
        ("  每日提醒+到期催办+上报上级", False, C_GRAY),
        ("[ ] 下次会议闭环通报", False, C_TEXT_DARK),
        ("  自动承接+待办统计+通报", False, C_GRAY),
        ("", False, C_BODY),
        ("全链路无人值守闭环完成", False, C_YELLOW_DEV),
    ], size=7.5, color=C_TEXT_DARK, lh=1.3)

    # 远景
    add_circ(slide, 31, ty-0.4, 0.8, C_GRAY)
    add_tb(slide, 29.5, ty+0.8, 4, 0.5, "2027年+", size=10, bold=True, color=C_GRAY, align=PP_ALIGN.CENTER)
    add_tb(slide, 28, ty+2.2, 5, 2, "多场景适配\n组织级推广\n数据分析与洞察", size=7.5, color=C_GRAY)

    # 当前标记
    nm = add_rrect(slide, 13.5, 12.0, 7, 0.9, C_ACCENT_BLUE)
    set_stext(nm, "当前进度: 20%  |  攻坚: 声纹+待办+纪要", size=8, bold=True, color=C_WHITE)
    add_source(slide, "时间线基于开发团队三人(阿统/阿品/阿栈)并行开发预估 | 视资源投入可调整")

    # ============================================================
    # PAGE 8: 核心价值总结 + 致谢
    # ============================================================
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_bg(slide, "bg-ai-future.jpg", alpha=0.5)

    vals = [
        ("对用户", [
            "开会前不用准备（全自动）",
            "开会时只管说话和决策",
            "开完会不用整理纪要",
            "待办自动找人、催、跟",
        ], C_ACCENT_BLUE),
        ("对组织", [
            "每次会议都对上次有交代",
            "每个决议都有人跟到底",
            "不再有开会/执行断层",
            "会议资产可追溯、可复用",
        ], C_GREEN_DONE),
        ("对效率", [
            "AI做掉90%运营工作",
            "纪要5分钟自动生成",
            "待办不再被遗忘",
            "新人无需交接即可上手",
        ], C_PURPLE),
    ]
    for i, (title, items, clr) in enumerate(vals):
        x = 2.5 + i*10
        add_tb(slide, x, 3.5, 8, 1, title, size=20, bold=True, color=C_WHITE)
        add_rect(slide, x, 4.8, 2, 0.12, clr)
        for j, item in enumerate(items):
            y = 5.3 + j*1.6
            add_circ(slide, x, y+0.1, 0.5, clr)
            add_tb(slide, x, y+0.1, 0.5, 0.5, str(j+1), size=9, bold=True, color=C_WHITE, align=PP_ALIGN.CENTER)
            add_tb(slide, x+0.8, y, 8, 0.8, item, size=11, color=RGBColor(0xCC, 0xD5, 0xE0))

    add_tb(slide, 2.5, 13.0, 28, 1.5,
           "&ldquo;人只需要到场、发言、决策&rdquo;",
           size=24, color=C_WHITE, align=PP_ALIGN.CENTER)
    add_tb(slide, 2.5, 14.8, 28, 1,
           "从会议发起到待办落地跟踪，全链路无人值守",
           size=13, color=RGBColor(0xA0, 0xB4, 0xD0), align=PP_ALIGN.CENTER)
    add_tb(slide, 2.5, 16.8, 10, 0.6, "谢谢！", size=28, bold=True, color=C_WHITE)
    add_tb(slide, 15, 16.8, 16, 0.7, "智能会议系统 / 让每一场会议都有交代", size=11, color=C_GRAY, align=PP_ALIGN.RIGHT)

    # === 保存 ===
    prs.save(OUTPUT)
    print(f"PPT已生成: {OUTPUT}")
    print(f"共 {len(prs.slides)} 页")

if __name__ == "__main__":
    main()

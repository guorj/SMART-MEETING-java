from pptx import Presentation
from pptx.util import Inches, Pt, Cm
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.chart.data import CategoryChartData
from pptx.enum.chart import XL_CHART_TYPE
from pptx.dml.effect import ShadowFormat

# 初始化PPT，16:9宽屏
prs = Presentation()
prs.slide_width = Inches(16)
prs.slide_height = Inches(9)

# 现代化配色方案
COLORS = {
    "primary": RGBColor(22, 93, 255),       # 品牌蓝
    "primary_light": RGBColor(111, 168, 255),# 浅蓝
    "success": RGBColor(0, 191, 165),       # 成功绿
    "warning": RGBColor(255, 159, 67),      # 警告黄
    "danger": RGBColor(255, 86, 86),        # 危险红
    "text_dark": RGBColor(29, 33, 41),      # 深灰文字
    "text_light": RGBColor(86, 92, 105),    # 浅灰文字
    "bg_light": RGBColor(247, 250, 255),    # 浅灰背景
    "white": RGBColor(255, 255, 255)        # 白色
}

# 统一卡片样式函数
def add_card(slide, x, y, w, h, bg_color=COLORS["white"], radius=0.3, shadow=True):
    card = slide.shapes.add_shape(1, x, y, w, h)  # 1 = 矩形
    card.fill.solid()
    card.fill.fore_color.rgb = bg_color
    card.line.fill.background()  # 无边框
    if shadow:
        card.shadow.type = ShadowFormat.SHADOW_OUTER
        card.shadow.blur_radius = Pt(10)
        card.shadow.offset_x = Pt(2)
        card.shadow.offset_y = Pt(4)
        card.shadow.transparency = 0.8
    return card

# 统一字体设置函数
def set_text(text_frame, text, font_size=14, bold=False, color=COLORS["text_dark"], align=PP_ALIGN.LEFT):
    text_frame.clear()
    p = text_frame.add_paragraph()
    p.text = text
    p.font.size = Pt(font_size)
    p.font.bold = bold
    p.font.color.rgb = color
    p.alignment = align
    p.font.name = "思源黑体"
    return p

# ------------------------------
# 第1页：封面页 (现代化设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])  # 空白页

# 顶部装饰条
top_bar = add_card(slide, Inches(0), Inches(0), Inches(16), Inches(0.8), bg_color=COLORS["primary"], shadow=False)

# 大标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(2), Inches(14), Inches(2))
set_text(title_box.text_frame, "智能会议系统", font_size=60, bold=True, color=COLORS["primary"], align=PP_ALIGN.CENTER)

# 副标题
subtitle_box = slide.shapes.add_textbox(Inches(1), Inches(3.8), Inches(14), Inches(1))
set_text(subtitle_box.text_frame, "全链路无人值守会议闭环系统 · 项目汇报", font_size=24, color=COLORS["text_light"], align=PP_ALIGN.CENTER)

# 进度卡片
progress_card = add_card(slide, Inches(6.5), Inches(5), Inches(3), Inches(2.5), bg_color=COLORS["bg_light"])
progress_title = slide.shapes.add_textbox(Inches(6.5), Inches(5.2), Inches(3), Inches(0.6))
set_text(progress_title.text_frame, "整体进度", font_size=16, bold=True, align=PP_ALIGN.CENTER)
progress_num = slide.shapes.add_textbox(Inches(6.5), Inches(5.8), Inches(3), Inches(1.2))
set_text(progress_num.text_frame, "20%", font_size=48, bold=True, color=COLORS["primary"], align=PP_ALIGN.CENTER)

# 页脚信息
footer_box = slide.shapes.add_textbox(Inches(1), Inches(8.2), Inches(14), Inches(0.5))
set_text(footer_box.text_frame, "汇报人：研发部 | 2026年5月", font_size=12, color=COLORS["text_light"], align=PP_ALIGN.CENTER)

# ------------------------------
# 第2页：目录页
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "汇报目录", font_size=32, bold=True, color=COLORS["primary"])

# 目录项
catalog = [
    "01  项目核心价值与用户体验",
    "02  当前功能进度明细",
    "03  闭环设计与数据飞轮",
    "04  价值对比与优势",
    "05  下一步规划"
]

y_pos = Inches(2.5)
for i, item in enumerate(catalog):
    # 序号圆点
    dot = slide.shapes.add_shape(9, Inches(2), y_pos + Inches(0.15), Inches(0.3), Inches(0.3))
    dot.fill.solid()
    dot.fill.fore_color.rgb = COLORS["primary"] if i < 5 else COLORS["text_light"]
    dot.line.fill.background()
    
    # 文字
    text_box = slide.shapes.add_textbox(Inches(2.6), y_pos, Inches(12), Inches(0.6))
    set_text(text_box.text_frame, item, font_size=20, color=COLORS["text_dark"])
    y_pos += Inches(0.9)

# ------------------------------
# 第3页：用户体验旅程 (卡片式设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "01  项目核心价值与用户体验", font_size=28, bold=True, color=COLORS["primary"])

# 副标题
subtitle_box = slide.shapes.add_textbox(Inches(1), Inches(1.6), Inches(14), Inches(0.5))
set_text(subtitle_box.text_frame, "人仅需发言+决策，其余全链路系统自动执行", font_size=14, color=COLORS["text_light"])

stages = [
    {"name": "会前24h", "human": "无操作", "system": "自动邀约\n会议室预约", "status": "未开始", "color": COLORS["text_light"]},
    {"name": "会前10min", "human": "点签到", "system": "会前盘点\n签到提醒", "status": "未开始", "color": COLORS["text_light"]},
    {"name": "会中60min", "human": "发言决策", "system": "AI主持\n实时转写", "status": "进行中", "color": COLORS["warning"]},
    {"name": "会后5min", "human": "无操作", "system": "自动生成\n结构化纪要", "status": "进行中", "color": COLORS["warning"]},
    {"name": "会后两周", "human": "点完成\n/延期", "system": "待办跟踪\n催办提醒", "status": "未开始", "color": COLORS["text_light"]},
    {"name": "下次会议", "human": "无操作", "system": "闭环通报\n自动循环", "status": "未开始", "color": COLORS["text_light"]},
]

col_width = Inches(2.2)
start_x = Inches(1.4)
y_pos = Inches(2.5)

# 阶段名称行
for i, stage in enumerate(stages):
    card = add_card(slide, start_x + i*col_width, y_pos, col_width-Inches(0.2), Inches(0.6), bg_color=COLORS["bg_light"])
    text_box = slide.shapes.add_textbox(start_x + i*col_width, y_pos + Inches(0.1), col_width-Inches(0.2), Inches(0.4))
    set_text(text_box.text_frame, stage["name"], font_size=12, bold=True, align=PP_ALIGN.CENTER)

# 人的动作行
y_pos += Inches(0.8)
row_title = slide.shapes.add_textbox(start_x - Inches(1), y_pos + Inches(0.3), Inches(0.9), Inches(0.6))
set_text(row_title.text_frame, "人的动作", font_size=12, align=PP_ALIGN.RIGHT)

for i, stage in enumerate(stages):
    card = add_card(slide, start_x + i*col_width, y_pos, col_width-Inches(0.2), Inches(1.2), bg_color=COLORS["white"])
    text_box = slide.shapes.add_textbox(start_x + i*col_width, y_pos + Inches(0.3), col_width-Inches(0.2), Inches(0.6))
    set_text(text_box.text_frame, stage["human"], font_size=12, align=PP_ALIGN.CENTER)

# 系统动作行
y_pos += Inches(1.4)
row_title = slide.shapes.add_textbox(start_x - Inches(1), y_pos + Inches(0.3), Inches(0.9), Inches(0.6))
set_text(row_title.text_frame, "系统动作", font_size=12, align=PP_ALIGN.RIGHT)

for i, stage in enumerate(stages):
    card = add_card(slide, start_x + i*col_width, y_pos, col_width-Inches(0.2), Inches(1.2), bg_color=stage["color"] if stage["status"] == "进行中" else COLORS["bg_light"])
    text_box = slide.shapes.add_textbox(start_x + i*col_width, y_pos + Inches(0.2), col_width-Inches(0.2), Inches(0.8))
    set_text(text_box.text_frame, stage["system"], font_size=11, align=PP_ALIGN.CENTER, color=COLORS["white"] if stage["status"] == "进行中" else COLORS["text_dark"])

# 状态行
y_pos += Inches(1.4)
row_title = slide.shapes.add_textbox(start_x - Inches(1), y_pos + Inches(0.15), Inches(0.9), Inches(0.4))
set_text(row_title.text_frame, "状态", font_size=12, align=PP_ALIGN.RIGHT)

for i, stage in enumerate(stages):
    text_box = slide.shapes.add_textbox(start_x + i*col_width, y_pos, col_width-Inches(0.2), Inches(0.4))
    set_text(text_box.text_frame, stage["status"], font_size=11, bold=True, color=stage["color"], align=PP_ALIGN.CENTER)

# ------------------------------
# 第4页：功能进度明细 (进度条设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "02  当前功能进度明细", font_size=28, bold=True, color=COLORS["primary"])

# 统计卡片
stats_card = add_card(slide, Inches(11.5), Inches(0.8), Inches(3.5), Inches(1.5), bg_color=COLORS["bg_light"])
stats_title = slide.shapes.add_textbox(Inches(11.5), Inches(1.0), Inches(3.5), Inches(0.5))
set_text(stats_title.text_frame, "整体完成度", font_size=14, bold=True, align=PP_ALIGN.CENTER)
stats_num = slide.shapes.add_textbox(Inches(11.5), Inches(1.4), Inches(3.5), Inches(0.6))
set_text(stats_num.text_frame, "20%", font_size=32, bold=True, color=COLORS["primary"], align=PP_ALIGN.CENTER)

# 功能列表
features = [
    {"stage": "会中模块", "name": "AI自动开场", "status": "完成", "progress": 100, "color": COLORS["success"]},
    {"stage": "会中模块", "name": "会议检点点名", "status": "完成", "progress": 100, "color": COLORS["success"]},
    {"stage": "会中模块", "name": "议题切换", "status": "完成", "progress": 100, "color": COLORS["success"]},
    {"stage": "会中模块", "name": "议题超时自动切换", "status": "完成", "progress": 100, "color": COLORS["success"]},
    {"stage": "会中模块", "name": "结束播报", "status": "完成", "progress": 100, "color": COLORS["success"]},
    {"stage": "会中模块", "name": "实时ASR+声纹", "status": "进行中", "progress": 60, "color": COLORS["warning"]},
    {"stage": "会中模块", "name": "纪要生成+飞书文档", "status": "进行中", "progress": 70, "color": COLORS["warning"]},
    {"stage": "会中模块", "name": "上次待办通报", "status": "开发中", "progress": 50, "color": COLORS["warning"]},
    {"stage": "会中模块", "name": "议题时间提醒", "status": "部分完成", "progress": 50, "color": COLORS["warning"]},
    {"stage": "会前/会后模块", "name": "自动创建会议、邀约、预约", "status": "未开始", "progress": 0, "color": COLORS["text_light"]},
    {"stage": "会前/会后模块", "name": "待办提取+飞书任务", "status": "未开始", "progress": 0, "color": COLORS["text_light"]},
    {"stage": "会前/会后模块", "name": "待办跟踪、催办、闭环", "status": "未开始", "progress": 0, "color": COLORS["text_light"]},
]

y_pos = Inches(2.2)
current_stage = ""
for i, feature in enumerate(features):
    if feature["stage"] != current_stage:
        # 阶段标题
        stage_box = slide.shapes.add_textbox(Inches(1), y_pos, Inches(10), Inches(0.5))
        set_text(stage_box.text_frame, feature["stage"], font_size=16, bold=True, color=COLORS["primary"])
        current_stage = feature["stage"]
        y_pos += Inches(0.6)
    
    # 功能名称
    name_box = slide.shapes.add_textbox(Inches(1.3), y_pos, Inches(4.5), Inches(0.4))
    set_text(name_box.text_frame, feature["name"], font_size=12)
    
    # 状态
    status_box = slide.shapes.add_textbox(Inches(6), y_pos, Inches(2), Inches(0.4))
    set_text(status_box.text_frame, feature["status"], font_size=12, color=feature["color"])
    
    # 进度条
    progress_bg = add_card(slide, Inches(8), y_pos + Inches(0.05), Inches(3), Inches(0.3), bg_color=COLORS["bg_light"], shadow=False)
    if feature["progress"] > 0:
        progress_fill = add_card(slide, Inches(8), y_pos + Inches(0.05), Inches(3 * feature["progress"] / 100), Inches(0.3), bg_color=feature["color"], shadow=False)
    
    # 进度百分比
    percent_box = slide.shapes.add_textbox(Inches(11.2), y_pos, Inches(0.8), Inches(0.4))
    set_text(percent_box.text_frame, f"{feature['progress']}%", font_size=12, color=feature["color"], align=PP_ALIGN.RIGHT)
    
    y_pos += Inches(0.45)

# ------------------------------
# 第5页：数据飞轮 (可视化设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "03  闭环设计与数据飞轮", font_size=28, bold=True, color=COLORS["primary"])

# 说明
desc_box = slide.shapes.add_textbox(Inches(1), Inches(1.6), Inches(14), Inches(0.5))
set_text(desc_box.text_frame, "每次会议承接上次决议，没有待办被遗忘，没有决议被搁置，形成永续循环", font_size=14, color=COLORS["text_light"])

# 飞轮节点
nodes = [
    {"text": "会议召开\n60%完成", "x": Inches(8.5), "y": Inches(2.5), "w": Inches(2.2), "h": Inches(1.1), "color": COLORS["warning"]},
    {"text": "纪要生成\n待办提取\n35%完成", "x": Inches(8.5), "y": Inches(5.5), "w": Inches(2.2), "h": Inches(1.3), "color": COLORS["warning"]},
    {"text": "完成/延期\n0%完成", "x": Inches(5), "y": Inches(5.5), "w": Inches(2.2), "h": Inches(1.1), "color": COLORS["bg_light"], "text_color": COLORS["text_light"]},
    {"text": "待办跟踪\n催办\n0%完成", "x": Inches(2.5), "y": Inches(4), "w": Inches(2.2), "h": Inches(1.1), "color": COLORS["bg_light"], "text_color": COLORS["text_light"]},
    {"text": "下次会议\n通报\n0%完成", "x": Inches(5), "y": Inches(2.5), "w": Inches(2.2), "h": Inches(1.1), "color": COLORS["bg_light"], "text_color": COLORS["text_light"]},
]

for node in nodes:
    card = add_card(slide, node["x"], node["y"], node["w"], node["h"], bg_color=node["color"])
    text_box = slide.shapes.add_textbox(node["x"], node["y"] + Inches(0.2), node["w"], node["h"] - Inches(0.4))
    set_text(text_box.text_frame, node["text"], font_size=12, bold=True, color=node.get("text_color", COLORS["white"] if node["color"] == COLORS["warning"] else COLORS["text_dark"]), align=PP_ALIGN.CENTER)

# 中心
center_card = add_card(slide, Inches(5.7), Inches(3.9), Inches(2.6), Inches(1.2), bg_color=COLORS["primary"])
center_text = slide.shapes.add_textbox(Inches(5.7), Inches(4.2), Inches(2.6), Inches(0.6))
set_text(center_text.text_frame, "整体进度", font_size=16, color=COLORS["white"], align=PP_ALIGN.CENTER)
center_num = slide.shapes.add_textbox(Inches(5.7), Inches(4.6), Inches(2.6), Inches(0.6))
set_text(center_num.text_frame, "20%", font_size=28, bold=True, color=COLORS["white"], align=PP_ALIGN.CENTER)

# ------------------------------
# 第6页：价值对比 (两栏卡片设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "04  价值对比与优势", font_size=28, bold=True, color=COLORS["primary"])

# 左侧卡片：传统会议
left_card = add_card(slide, Inches(1), Inches(2), Inches(6.5), Inches(6))
left_title = slide.shapes.add_textbox(Inches(1), Inches(2.2), Inches(6.5), Inches(0.8))
set_text(left_title.text_frame, "传统会议", font_size=24, bold=True, color=COLORS["text_light"], align=PP_ALIGN.CENTER)

left_points = [
    "❌  会前需要人工发邀约、预约会议室",
    "❌  会中需要人工主持、计时、记录",
    "❌  会后需要人工写纪要、手动分发",
    "❌  待办跟踪靠人记、靠人催，容易遗忘",
    "❌  开完就散，下次会议从头来，没有闭环"
]

y_pos = Inches(3.2)
for point in left_points:
    text_box = slide.shapes.add_textbox(Inches(1.5), y_pos, Inches(5.5), Inches(0.6))
    set_text(text_box.text_frame, point, font_size=14, color=COLORS["text_light"])
    y_pos += Inches(0.8)

# 右侧卡片：智能会议
right_card = add_card(slide, Inches(8.5), Inches(2), Inches(6.5), Inches(6))
right_title = slide.shapes.add_textbox(Inches(8.5), Inches(2.2), Inches(6.5), Inches(0.8))
set_text(right_title.text_frame, "智能会议", font_size=24, bold=True, color=COLORS["primary"], align=PP_ALIGN.CENTER)

right_points = [
    "✅  会前系统自动创建会议、邀约、预约会议室",
    "✅  会中AI自动主持、计时、实时转写",
    "✅  会后LLM自动生成结构化纪要，自动同步飞书",
    "✅  待办自动提取、自动催办、延期自动上报",
    "✅  每次会议承接上次，形成永续闭环，永不遗忘"
]

y_pos = Inches(3.2)
for point in right_points:
    text_box = slide.shapes.add_textbox(Inches(9), y_pos, Inches(5.5), Inches(0.6))
    set_text(text_box.text_frame, point, font_size=14, color=COLORS["text_dark"])
    y_pos += Inches(0.8)

# ------------------------------
# 第7页：下一步规划 (时间轴设计)
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 标题
title_box = slide.shapes.add_textbox(Inches(1), Inches(0.8), Inches(14), Inches(0.8))
set_text(title_box.text_frame, "05  下一步规划", font_size=28, bold=True, color=COLORS["primary"])

plans = [
    {"time": "2026年6月底", "title": "会中模块全功能上线", "desc": "完成声纹识别、待办通报、完整纪要生成功能，会中流程闭环"},
    {"time": "2026年8月底", "title": "会前模块上线", "desc": "完成自动邀约、签到、会议室预约功能，会前流程全自动化"},
    {"time": "2026年10月底", "title": "全系统正式商用", "desc": "完成会后待办跟踪、催办、闭环通报全流程，全链路无人值守"},
]

# 时间轴竖线
timeline_line = add_card(slide, Inches(2), Inches(2.2), Inches(0.1), Inches(6), bg_color=COLORS["primary"], shadow=False)

y_pos = Inches(2)
for plan in plans:
    # 时间点圆点
    dot = slide.shapes.add_shape(9, Inches(1.85), y_pos + Inches(0.25), Inches(0.4), Inches(0.4))
    dot.fill.solid()
    dot.fill.fore_color.rgb = COLORS["primary"]
    dot.line.fill.background()
    
    # 时间标签
    time_box = slide.shapes.add_textbox(Inches(0.5), y_pos + Inches(0.2), Inches(1.2), Inches(0.5))
    set_text(time_box.text_frame, plan["time"], font_size=14, bold=True, color=COLORS["primary"], align=PP_ALIGN.RIGHT)
    
    # 内容卡片
    card = add_card(slide, Inches(2.8), y_pos, Inches(12), Inches(1.5))
    plan_title = slide.shapes.add_textbox(Inches(3.2), y_pos + Inches(0.2), Inches(11), Inches(0.6))
    set_text(plan_title.text_frame, plan["title"], font_size=20, bold=True, color=COLORS["primary"])
    plan_desc = slide.shapes.add_textbox(Inches(3.2), y_pos + Inches(0.8), Inches(11), Inches(0.6))
    set_text(plan_desc.text_frame, plan["desc"], font_size=14, color=COLORS["text_light"])
    
    y_pos += Inches(2)

# ------------------------------
# 第8页：结尾页
# ------------------------------
slide = prs.slides.add_slide(prs.slide_layouts[5])

# 底部装饰条
bottom_bar = add_card(slide, Inches(0), Inches(8.2), Inches(16), Inches(0.8), bg_color=COLORS["primary"], shadow=False)

# 致谢文字
thank_text = slide.shapes.add_textbox(Inches(1), Inches(3.5), Inches(14), Inches(1.5))
set_text(thank_text.text_frame, "谢谢观看", font_size=60, bold=True, color=COLORS["primary"], align=PP_ALIGN.CENTER)

# 联系方式
contact_text = slide.shapes.add_textbox(Inches(1), Inches(5), Inches(14), Inches(0.8))
set_text(contact_text.text_frame, "欢迎提出宝贵意见", font_size=20, color=COLORS["text_light"], align=PP_ALIGN.CENTER)

# 保存PPT
output_path = "/mnt/d/openclaw/workspace-clone/projects/smart-meeting-java/智能会议系统项目汇报PPT_优化版.pptx"
prs.save(output_path)
print(f"优化版PPT生成成功：{output_path}")

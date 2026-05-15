from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.chart.data import CategoryChartData, XyChartData, BubbleChartData
from pptx.enum.chart import XL_CHART_TYPE
from pptx.util import Cm

# 初始化PPT，使用16:9模板
prs = Presentation()
prs.slide_width = Inches(16)
prs.slide_height = Inches(9)

# 品牌颜色定义
BRAND_BLUE = RGBColor(22, 93, 255)
LIGHT_GRAY = RGBColor(245, 247, 250)
DARK_GRAY = RGBColor(29, 33, 41)
GREEN = RGBColor(0, 191, 165)
YELLOW = RGBColor(255, 193, 7)
GRAY = RGBColor(173, 181, 189)

# 统一字体设置函数
def set_font(text_frame, font_size=14, bold=False, color=DARK_GRAY):
    for p in text_frame.paragraphs:
        p.font.size = Pt(font_size)
        p.font.bold = bold
        p.font.color.rgb = color
        p.font.name = "思源黑体"
        p.alignment = PP_ALIGN.LEFT

# ------------------------------
# 第1页：封面页
# ------------------------------
slide_layout = prs.slide_layouts[5]  # 空白布局
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(2), Inches(14), Inches(2))
tf = title.text_frame
tf.text = "智能会议系统项目汇报"
p = tf.paragraphs[0]
p.font.size = Pt(44)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE
p.alignment = PP_ALIGN.CENTER

# 副标题
subtitle = slide.shapes.add_textbox(Inches(1), Inches(3.5), Inches(14), Inches(1))
tf = subtitle.text_frame
tf.text = "全链路无人值守会议闭环系统"
p = tf.paragraphs[0]
p.font.size = Pt(24)
p.font.color.rgb = DARK_GRAY
p.alignment = PP_ALIGN.CENTER

# 进度环形图
chart_data = CategoryChartData()
chart_data.categories = ["完成", "未完成"]
chart_data.add_series("进度", [20, 80])
x, y, cx, cy = Inches(6.5), Inches(4.5), Inches(3), Inches(3)
chart = slide.shapes.add_chart(XL_CHART_TYPE.DOUGHNUT, x, y, cx, cy, chart_data).chart
chart.series[0].points[0].format.fill.solid()
chart.series[0].points[0].format.fill.fore_color.rgb = BRAND_BLUE
chart.series[0].points[1].format.fill.solid()
chart.series[0].points[1].format.fill.fore_color.rgb = LIGHT_GRAY
chart.has_legend = False

# 进度文字
progress_text = slide.shapes.add_textbox(Inches(7.5), Inches(5.5), Inches(1), Inches(1))
tf = progress_text.text_frame
tf.text = "20%"
p = tf.paragraphs[0]
p.font.size = Pt(32)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE
p.alignment = PP_ALIGN.CENTER

# 页脚信息
footer = slide.shapes.add_textbox(Inches(1), Inches(8), Inches(14), Inches(0.5))
tf = footer.text_frame
tf.text = "汇报人：研发部 | 2026年5月14日"
p = tf.paragraphs[0]
p.font.size = Pt(12)
p.font.color.rgb = GRAY
p.alignment = PP_ALIGN.CENTER

# ------------------------------
# 第2页：用户体验旅程
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(0.5), Inches(14), Inches(0.8))
tf = title.text_frame
tf.text = "一、用户视角：一场会议的完整体验"
p = tf.paragraphs[0]
p.font.size = Pt(28)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE

# 旅程图说明
desc = slide.shapes.add_textbox(Inches(1), Inches(1.3), Inches(14), Inches(0.5))
tf = desc.text_frame
tf.text = "整体进度：20% | 人仅需发言决策，全链路系统自动执行"
p = tf.paragraphs[0]
p.font.size = Pt(14)
p.font.color.rgb = DARK_GRAY

# 阶段表头
stages = ["会前24h", "会前10min", "会中60min", "会后5min", "会后两周", "下次会议"]
col_width = Inches(2.2)
start_x = Inches(1)
y_pos = Inches(2)

for i, stage in enumerate(stages):
    box = slide.shapes.add_textbox(start_x + i*col_width, y_pos, col_width, Inches(0.6))
    tf = box.text_frame
    tf.text = stage
    p = tf.paragraphs[0]
    p.font.size = Pt(14)
    p.font.bold = True
    p.alignment = PP_ALIGN.CENTER
    p.font.color.rgb = DARK_GRAY

# 人的动作行
y_pos += Inches(0.6)
row_title = slide.shapes.add_textbox(start_x - Inches(0.8), y_pos, Inches(0.8), Inches(0.8))
tf = row_title.text_frame
tf.text = "人的动作"
p = tf.paragraphs[0]
p.font.size = Pt(12)
p.alignment = PP_ALIGN.RIGHT
p.font.color.rgb = DARK_GRAY

human_actions = ["无操作", "点签到", "发言决策", "无操作", "点完成\n/延期", "无操作"]
for i, action in enumerate(human_actions):
    box = slide.shapes.add_textbox(start_x + i*col_width, y_pos, col_width, Inches(0.8))
    tf = box.text_frame
    tf.text = action
    set_font(tf, 12, color=DARK_GRAY)
    tf.paragraphs[0].alignment = PP_ALIGN.CENTER

# 系统动作行
y_pos += Inches(0.8)
row_title = slide.shapes.add_textbox(start_x - Inches(0.8), y_pos, Inches(0.8), Inches(0.8))
tf = row_title.text_frame
tf.text = "系统动作"
p = tf.paragraphs[0]
p.font.size = Pt(12)
p.alignment = PP_ALIGN.RIGHT
p.font.color.rgb = DARK_GRAY

system_actions = [
    "自动邀约\n会议室预约",
    "会前盘点\n签到提醒",
    "AI主持\n实时转写",
    "自动生成\n结构化纪要",
    "待办跟踪\n催办提醒",
    "闭环通报\n自动循环"
]
system_status = [GRAY, GRAY, YELLOW, YELLOW, GRAY, GRAY]
for i, action in enumerate(system_actions):
    box = slide.shapes.add_textbox(start_x + i*col_width, y_pos, col_width, Inches(0.8))
    tf = box.text_frame
    tf.text = action
    set_font(tf, 11, color=DARK_GRAY)
    tf.paragraphs[0].alignment = PP_ALIGN.CENTER
    # 背景色
    box.fill.solid()
    box.fill.fore_color.rgb = system_status[i]

# 进度行
y_pos += Inches(0.8)
row_title = slide.shapes.add_textbox(start_x - Inches(0.8), y_pos, Inches(0.8), Inches(0.6))
tf = row_title.text_frame
tf.text = "进度"
p = tf.paragraphs[0]
p.font.size = Pt(12)
p.alignment = PP_ALIGN.RIGHT
p.font.color.rgb = DARK_GRAY

progress_texts = ["未开始", "未开始", "进行中", "进行中", "未开始", "未开始"]
progress_colors = [GRAY, GRAY, YELLOW, YELLOW, GRAY, GRAY]
for i, text in enumerate(progress_texts):
    box = slide.shapes.add_textbox(start_x + i*col_width, y_pos, col_width, Inches(0.6))
    tf = box.text_frame
    tf.text = text
    p = tf.paragraphs[0]
    p.font.size = Pt(11)
    p.font.bold = True
    p.alignment = PP_ALIGN.CENTER
    p.font.color.rgb = progress_colors[i]

# ------------------------------
# 第3页：功能进度明细
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(0.5), Inches(14), Inches(0.8))
tf = title.text_frame
tf.text = "二、功能进度明细"
p = tf.paragraphs[0]
p.font.size = Pt(28)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE

# 表格
rows = 16
cols = 4
left = Inches(1)
top = Inches(1.5)
width = Inches(14)
height = Inches(6.5)

table = slide.shapes.add_table(rows, cols, left, top, width, height).table

# 表头
table.cell(0, 0).text = "阶段"
table.cell(0, 1).text = "功能点"
table.cell(0, 2).text = "状态"
table.cell(0, 3).text = "完成度"
for i in range(4):
    cell = table.cell(0, i)
    cell.fill.solid()
    cell.fill.fore_color.rgb = BRAND_BLUE
    p = cell.text_frame.paragraphs[0]
    p.font.size = Pt(14)
    p.font.bold = True
    p.font.color.rgb = RGBColor(255,255,255)
    p.alignment = PP_ALIGN.CENTER

# 内容
features = [
    ["会前24h", "自动创建会议", "⬜ 未开始", "0%"],
    ["会前24h", "飞书邀约卡片", "⬜ 未开始", "0%"],
    ["会前24h", "参会确认+关键人判断", "⬜ 未开始", "0%"],
    ["会前24h", "日历+会议室预约", "⬜ 未开始", "0%"],
    ["会前10min", "盘点卡片+签到", "⬜ 未开始", "0%"],
    ["会中", "AI自动开场", "🟢 完成", "100%"],
    ["会中", "会议检点点名", "🟢 完成", "100%"],
    ["会中", "上次待办通报", "🟡 开发中", "50%"],
    ["会中", "议题切换", "🟢 完成", "100%"],
    ["会中", "实时ASR+声纹", "🟡 进行中", "60%"],
    ["会中", "议题时间提醒", "🟡 部分完成", "50%"],
    ["会中", "议题超时自动切换", "🟢 完成", "100%"],
    ["会后", "结束播报", "🟢 完成", "100%"],
    ["会后", "纪要生成+飞书文档", "🟡 进行中", "70%"],
    ["会后", "待办提取+飞书任务", "⬜ 未开始", "0%"]
]

for i, feature in enumerate(features):
    for j in range(4):
        cell = table.cell(i+1, j)
        cell.text = feature[j]
        p = cell.text_frame.paragraphs[0]
        p.font.size = Pt(12)
        p.alignment = PP_ALIGN.CENTER
        if j == 2:
            if "🟢" in feature[j]:
                p.font.color.rgb = GREEN
            elif "🟡" in feature[j]:
                p.font.color.rgb = YELLOW
            else:
                p.font.color.rgb = GRAY

# ------------------------------
# 第4页：数据飞轮
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(0.5), Inches(14), Inches(0.8))
tf = title.text_frame
tf.text = "三、数据视角：永不停歇的闭环"
p = tf.paragraphs[0]
p.font.size = Pt(28)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE

# 飞轮说明
desc = slide.shapes.add_textbox(Inches(1), Inches(1.3), Inches(14), Inches(0.5))
tf = desc.text_frame
tf.text = "每次会议承接上次决议，没有待办被遗忘，没有决议被搁置"
p = tf.paragraphs[0]
p.font.size = Pt(14)
p.font.color.rgb = DARK_GRAY

# 飞轮节点
nodes = [
    {"text": "会议召开\n🟢🟡 60%完成", "x": Inches(8.5), "y": Inches(2.5), "w": Inches(2), "h": Inches(1), "color": YELLOW},
    {"text": "纪要生成\n待办提取\n🟡⬜ 35%完成", "x": Inches(8.5), "y": Inches(5.5), "w": Inches(2), "h": Inches(1.2), "color": YELLOW},
    {"text": "完成/延期\n⬜ 0%完成", "x": Inches(5.5), "y": Inches(5.5), "w": Inches(2), "h": Inches(1), "color": GRAY},
    {"text": "待办跟踪\n催办\n⬜ 0%完成", "x": Inches(2.5), "y": Inches(4), "w": Inches(2), "h": Inches(1), "color": GRAY},
    {"text": "下次会议\n通报\n⬜ 0%完成", "x": Inches(5.5), "y": Inches(2.5), "w": Inches(2), "h": Inches(1), "color": GRAY},
]

for node in nodes:
    box = slide.shapes.add_textbox(node["x"], node["y"], node["w"], node["h"])
    tf = box.text_frame
    tf.text = node["text"]
    set_font(tf, 12, bold=True, color=DARK_GRAY)
    tf.paragraphs[0].alignment = PP_ALIGN.CENTER
    box.fill.solid()
    box.fill.fore_color.rgb = node["color"]
    box.line.color.rgb = BRAND_BLUE
    box.line.width = Pt(1)

# 中心进度
center = slide.shapes.add_textbox(Inches(5.5), Inches(4), Inches(2), Inches(1))
tf = center.text_frame
tf.text = "整体进度\n20%"
p = tf.paragraphs[0]
p.font.size = Pt(20)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE
p.alignment = PP_ALIGN.CENTER

# ------------------------------
# 第5页：传统vs智能对比
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(0.5), Inches(14), Inches(0.8))
tf = title.text_frame
tf.text = "四、价值对比：传统会议 vs 智能会议"
p = tf.paragraphs[0]
p.font.size = Pt(28)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE

# 对比表格
rows = 6
cols = 3
left = Inches(1)
top = Inches(1.5)
width = Inches(14)
height = Inches(6.5)

table = slide.shapes.add_table(rows, cols, left, top, width, height).table

# 表头
table.cell(0, 0).text = "维度"
table.cell(0, 1).text = "传统会议"
table.cell(0, 2).text = "智能会议"
for i in range(3):
    cell = table.cell(0, i)
    cell.fill.solid()
    cell.fill.fore_color.rgb = BRAND_BLUE
    p = cell.text_frame.paragraphs[0]
    p.font.size = Pt(14)
    p.font.bold = True
    p.font.color.rgb = RGBColor(255,255,255)
    p.alignment = PP_ALIGN.CENTER

# 内容
comparisons = [
    ["会前准备", "人工发邀约、预约会议室", "✅ 系统自动创建+邀约+预约"],
    ["会中主持", "人工主持、计时、记录", "✅ AI主持+计时+实时转写"],
    ["会后整理", "人工写纪要、分发", "✅ LLM自动生成+飞书同步"],
    ["待办跟踪", "靠人记、靠人催", "✅ 自动提取+自动催办+延期上报"],
    ["闭环", "开完就散，下次从头来", "✅ 每次会议承接上次，永不遗忘"]
]

for i, comp in enumerate(comparisons):
    for j in range(3):
        cell = table.cell(i+1, j)
        cell.text = comp[j]
        p = cell.text_frame.paragraphs[0]
        p.font.size = Pt(14)
        p.alignment = PP_ALIGN.CENTER if j ==0 else PP_ALIGN.LEFT

# ------------------------------
# 第6页：下一步计划
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 标题
title = slide.shapes.add_textbox(Inches(1), Inches(0.5), Inches(14), Inches(0.8))
tf = title.text_frame
tf.text = "五、下一步规划"
p = tf.paragraphs[0]
p.font.size = Pt(28)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE

# 计划内容
plans = [
    {"time": "1-2个月（6月底）", "content": "完成会中模块全功能上线：声纹识别、待办通报、完整纪要生成"},
    {"time": "3-4个月（8月底）", "content": "完成会前模块：自动邀约、签到、会议室预约功能上线"},
    {"time": "5-6个月（10月底）", "content": "完成会后待办跟踪、催办、闭环通报全流程，系统正式商用"}
]

y_pos = Inches(2)
for i, plan in enumerate(plans):
    # 时间标签
    time_box = slide.shapes.add_textbox(Inches(2), y_pos, Inches(2.5), Inches(0.8))
    tf = time_box.text_frame
    tf.text = plan["time"]
    p = tf.paragraphs[0]
    p.font.size = Pt(16)
    p.font.bold = True
    p.font.color.rgb = BRAND_BLUE
    p.alignment = PP_ALIGN.LEFT
    
    # 内容
    content_box = slide.shapes.add_textbox(Inches(5), y_pos, Inches(9), Inches(0.8))
    tf = content_box.text_frame
    tf.text = plan["content"]
    p = tf.paragraphs[0]
    p.font.size = Pt(16)
    p.font.color.rgb = DARK_GRAY
    p.alignment = PP_ALIGN.LEFT
    
    y_pos += Inches(1.5)

# ------------------------------
# 第7页：结尾页
# ------------------------------
slide = prs.slides.add_slide(slide_layout)

# 致谢
title = slide.shapes.add_textbox(Inches(1), Inches(3.5), Inches(14), Inches(2))
tf = title.text_frame
tf.text = "谢谢观看"
p = tf.paragraphs[0]
p.font.size = Pt(44)
p.font.bold = True
p.font.color.rgb = BRAND_BLUE
p.alignment = PP_ALIGN.CENTER

# 保存PPT
output_path = "/mnt/d/openclaw/workspace-clone/projects/smart-meeting-java/智能会议系统项目汇报PPT.pptx"
prs.save(output_path)
print(f"PPT生成成功：{output_path}")

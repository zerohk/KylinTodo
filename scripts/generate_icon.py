#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为「大智桌面日历」生成应用图标。

设计说明
--------
图标需要在小到 16x16 的尺寸下仍然可辨识，因此元素尽量少：
  - 圆角方形底 + 麒麟蓝渐变的日历本外形
  - 顶部装订孔（区分"日历"与普通方形）
  - 白色日期区，显示醒目数字
  - 右下角对勾（表达待办/已完成，也是与纯日历应用的区分点）

刻意不使用中文字符作为主视觉：小尺寸下汉字会糊成一团。

输出
----
  src/main/resources/icon/dazhi-calendar.png    512x512 主图标
  src/main/resources/icon/dazhi-calendar-{256,128,64,48,32,16}.png
  src/main/resources/icon/dazhi-calendar.ico    Windows 多尺寸 ICO

用法
----
  python scripts/generate_icon.py

依赖：Pillow（工作区自带）
"""

import io
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    print("需要 Pillow：pip install Pillow", file=sys.stderr)
    sys.exit(1)

# ---------------------------------------------------------------- 配色
# 与 KylinTheme.kt 的 KylinBlue 系列保持一致，避免图标与应用界面脱节
BLUE_TOP = (32, 130, 214)      # #2082D6 稍亮的麒麟蓝
BLUE_BOTTOM = (17, 88, 158)    # #11589E 深一档，构成自上而下的渐变
WHITE = (255, 255, 255)
PAPER = (247, 250, 253)        # 日期区纸面，略带冷色更清爽
CHECK = (46, 125, 50)          # WorkdayGreen 同色，表达"完成"
SHADOW = (0, 0, 0, 46)

# 超采样倍率：以 4 倍尺寸绘制再缩小，获得平滑边缘（等效抗锯齿）
SS = 4

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "icon",
)


def rounded_mask(size, radius):
    """生成圆角矩形遮罩。"""
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def vertical_gradient(size, top, bottom):
    """生成自上而下的线性渐变图。"""
    grad = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / max(size - 1, 1)
        grad.putpixel(
            (0, y),
            (
                int(top[0] + (bottom[0] - top[0]) * t),
                int(top[1] + (bottom[1] - top[1]) * t),
                int(top[2] + (bottom[2] - top[2]) * t),
            ),
        )
    return grad.resize((size, size), Image.NEAREST)


def render_base(px):
    """按 px 像素绘制 1 倍图标（内部先按 SS 倍绘制再缩小）。"""
    S = px * SS
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # ---- 阴影：让图标在浅色桌面上有立体感 ----
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    inset = int(S * 0.055)
    sd.rounded_rectangle(
        (inset, inset + int(S * 0.018), S - inset, S - inset + int(S * 0.018)),
        radius=int(S * 0.145),
        fill=SHADOW,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.022))
    canvas = Image.alpha_composite(canvas, shadow)

    # ---- 主体：圆角方形 + 渐变 ----
    body = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    body.paste(vertical_gradient(S, BLUE_TOP, BLUE_BOTTOM).convert("RGBA"), (0, 0))
    body.putalpha(rounded_mask(S, int(S * 0.145)))
    canvas = Image.alpha_composite(canvas, body)

    d = ImageDraw.Draw(canvas)

    # ---- 顶部装订孔：两个小圆点，暗示"日历本" ----
    ring_r = int(S * 0.028)
    ring_y = int(S * 0.185)
    for cx in (int(S * 0.335), int(S * 0.665)):
        d.ellipse(
            (cx - ring_r, ring_y - ring_r, cx + ring_r, ring_y + ring_r),
            fill=(255, 255, 255, 60),
        )

    # ---- 白色日期区 ----
    pad = int(S * 0.155)
    top = int(S * 0.255)
    d.rounded_rectangle(
        (pad, top, S - pad, S - pad),
        radius=int(S * 0.085),
        fill=PAPER,
    )

    # ---- 日期数字：用矩形笔画自绘，避免依赖字体文件 ----
    # 绘制 "17"：在日期区内居中，笔画为圆角矩形，比例经过调校
    draw_digits(d, S, pad, top)

    # ---- 右下角对勾：表达待办完成 ----
    draw_check(d, S)

    return canvas.resize((px, px), Image.LANCZOS)


def load_digit_font(px):
    """加载用于绘制日期数字的字体。

    手绘矩形笔画很难把数字画得干净（实测"1"的起笔与"7"的斜笔都容易出锯齿），
    因此改为渲染系统无衬线粗体。按优先级尝试各平台常见字体，
    全部缺失时回落 Pillow 内置位图字体（虽小但可用）。
    """
    candidates = [
        "C:/Windows/Fonts/msyhbd.ttc",   # 微软雅黑 Bold
        "C:/Windows/Fonts/segoeuib.ttf",  # Segoe UI Bold
        "C:/Windows/Fonts/arialbd.ttf",   # Arial Bold
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",  # 麒麟常见
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, px), os.path.basename(path)
            except Exception:
                continue
    return ImageFont.load_default(), "Pillow 内置字体"


def draw_digits(d, S, pad, top):
    """在日期区内渲染日期数字。

    数字占日期区左侧约 2/3 宽度，右下角刻意留白给对勾徽标，
    避免两者视觉上打架。
    """
    area_left, area_top = pad, top
    area_right, area_bottom = S - pad, S - pad
    area_w = area_right - area_left
    area_h = area_bottom - area_top

    # 字号按可用高度的比例确定，再用实际边界框居中
    font_px = int(area_h * 0.52)
    font, _ = load_digit_font(font_px)

    text = "17"
    bbox = d.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    # 水平方向略偏左，纵向居中偏上，为右下角对勾留出空间
    x = area_left + int(area_w * 0.14) - bbox[0]
    y = area_top + int((area_h - th) * 0.42) - bbox[1]

    d.text((x, y), text, font=font, fill=(26, 92, 156))


def draw_check(d, S):
    """右下角绿色对勾：表达"待办已完成"。

    位置与尺寸经过调整：早先版本偏大且过于居中，压住了日历主体。
    现在缩小并推向角落，让"日历"这一主视觉保持完整。
    """
    r = int(S * 0.150)
    cx, cy = int(S * 0.800), int(S * 0.795)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=CHECK)
    d.ellipse(
        (cx - r, cy - r, cx + r, cy + r),
        outline=PAPER,
        width=max(int(S * 0.009), 1),
    )

    w = max(int(S * 0.026), 2)
    p1 = (cx - int(r * 0.46), cy + int(r * 0.02))
    p2 = (cx - int(r * 0.10), cy + int(r * 0.38))
    p3 = (cx + int(r * 0.48), cy - int(r * 0.32))
    for a, b in ((p1, p2), (p2, p3)):
        d.line((a, b), fill=PAPER, width=w, joint="curve")
    for p in (p1, p2, p3):
        d.ellipse(
            (p[0] - w // 2, p[1] - w // 2, p[0] + w // 2, p[1] + w // 2),
            fill=PAPER,
        )


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # 主图标与各尺寸
    sizes = [512, 256, 128, 64, 48, 32, 16]
    images = {}
    for s in sizes:
        img = render_base(s)
        images[s] = img
        name = "dazhi-calendar.png" if s == 512 else f"dazhi-calendar-{s}.png"
        path = os.path.join(OUT_DIR, name)
        img.save(path)
        print(f"  写入 {os.path.relpath(path)}  ({os.path.getsize(path)} 字节)")

    # Windows ICO：内嵌多个尺寸，系统会按需要挑选
    ico_path = os.path.join(OUT_DIR, "dazhi-calendar.ico")
    images[512].save(
        ico_path,
        format="ICO",
        sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )
    print(f"  写入 {os.path.relpath(ico_path)}  ({os.path.getsize(ico_path)} 字节)")

    # Linux 桌面项通常也接受 PNG，无需额外格式

    print("\n完成。")


if __name__ == "__main__":
    main()

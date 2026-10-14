#!/usr/bin/env python3
"""
Advanced Motion Design Promo Video Generator for Loom.
Produces a 1080x1920 60fps / 30fps vertical MP4 with:
- Realistic smartphone device frame with OLED screen
- Chaotic notification shake vs calm Loom transition
- Kinetic typography with spring physics
- Dynamic time-of-day routine morphing inside phone
- Glassmorphic glowing metric cards with subtle particle atmosphere
- Light-sweep CTA button reflection
"""

import sys
import os
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import imageio_ffmpeg

WIDTH = 1080
HEIGHT = 1920
FPS = 30
DURATION_SEC = 12
TOTAL_FRAMES = FPS * DURATION_SEC

OUTPUT_PATH = os.path.abspath("docs/loom_promo.mp4")

# Load fonts
FONT_PATH = "/System/Library/Fonts/Helvetica.ttc"
if not os.path.exists(FONT_PATH):
    FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"

def get_font(size, bold=False):
    try:
        idx = 1 if bold else 0
        return ImageFont.truetype(FONT_PATH, size=size, index=idx)
    except Exception:
        return ImageFont.truetype(FONT_PATH, size=size)

FONT_HERO = get_font(84, bold=True)
FONT_TITLE = get_font(58, bold=True)
FONT_SUBTITLE = get_font(38, bold=False)
FONT_BODY = get_font(32, bold=False)
FONT_BODY_BOLD = get_font(32, bold=True)
FONT_BADGE = get_font(40, bold=True)
FONT_CARD_NUM = get_font(72, bold=True)
FONT_SMALL = get_font(26, bold=False)

# Load Logo
LOGO_PATH = os.path.abspath("docs/app_icon_512.png")
LOGO_IMG = Image.open(LOGO_PATH).convert("RGBA") if os.path.exists(LOGO_PATH) else None

def ease_out_cubic(x):
    return 1 - pow(1 - max(0.0, min(1.0, x)), 3)

def ease_out_back(x, s=1.70158):
    x = max(0.0, min(1.0, x)) - 1
    return x * x * ((s + 1) * x + s) + 1

def ease_in_out_quad(x):
    x = max(0.0, min(1.0, x))
    return 2 * x * x if x < 0.5 else 1 - pow(-2 * x + 2, 2) / 2

def draw_rounded_rect(draw, bbox, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(bbox, radius=radius, fill=fill, outline=outline, width=width)

# Vector Icon Drawers
def draw_clock_icon(draw, cx, cy, r=18, color=(240, 240, 240)):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=3)
    draw.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=color)
    draw.line([cx, cy, cx, cy - r + 5], fill=color, width=3)
    draw.line([cx, cy, cx + r - 6, cy], fill=color, width=3)

def draw_lock_icon(draw, cx, cy, w=14, h=15, color=(74, 222, 128)):
    draw.rounded_rectangle([cx - w, cy - h // 2, cx + w, cy + h], radius=4, fill=color)
    draw.arc([cx - w + 4, cy - h - 4, cx + w - 4, cy], start=180, end=0, fill=color, width=3)

def draw_download_icon(draw, cx, cy, size=15, color=(0, 0, 0)):
    draw.line([cx, cy - size, cx, cy + size - 6], fill=color, width=4)
    draw.polygon([(cx - size // 2 - 2, cy + size - 10), (cx + size // 2 + 2, cy + size - 10), (cx, cy + size - 2)], fill=color)
    draw.line([cx - size, cy + size + 4, cx + size, cy + size + 4], fill=color, width=4)

def draw_phone_frame(draw, px, py, pw, ph, glow_color=(20, 35, 60)):
    # Outer subtle shadow
    for i in range(16, 0, -4):
        alpha = int(12 * (1.0 - i / 16))
        draw_rounded_rect(draw, [px - i, py - i, px + pw + i, py + ph + i], 72 + i, outline=(glow_color[0], glow_color[1], glow_color[2], alpha), width=3)

    # Dark titanium chassis
    draw_rounded_rect(draw, [px, py, px + pw, py + ph], 68, fill=(10, 11, 14), outline=(45, 50, 62), width=3)

    # Screen cutout (inner OLED)
    sx, sy = px + 16, py + 16
    sw, sh = pw - 32, ph - 32
    draw_rounded_rect(draw, [sx, sy, sx + sw, sy + sh], 52, fill=(0, 0, 0))

    # Dynamic Island / Camera Pill
    draw_rounded_rect(draw, [px + pw // 2 - 45, sy + 16, px + pw // 2 + 45, sy + 42], 13, fill=(18, 19, 23))

    # Home bar
    draw_rounded_rect(draw, [px + pw // 2 - 80, sy + sh - 22, px + pw // 2 + 80, sy + sh - 15], 4, fill=(100, 105, 115))

    return sx, sy, sw, sh

def render_frame(f):
    img = Image.new("RGBA", (WIDTH, HEIGHT), (6, 7, 10, 255))
    draw = ImageDraw.Draw(img)
    t = f / FPS

    # Subtle ambient background lighting (breathing radial glow)
    glow_pulse = 0.5 + 0.5 * math.sin(t * 1.5)
    center_glow_r = int(600 + 50 * glow_pulse)
    for r in range(center_glow_r, 200, -80):
        g_alpha = int(6 * (1.0 - r / center_glow_r))
        draw.ellipse([WIDTH // 2 - r, 960 - r, WIDTH // 2 + r, 960 + r], fill=(15, 25, 45, g_alpha))

    # Floating particles (subtle dust motes)
    for i in range(12):
        px = int((i * 137 + t * 25) % WIDTH)
        py = int((i * 241 + t * 15 * (1 if i % 2 == 0 else -1)) % HEIGHT)
        p_alpha = int(40 + 30 * math.sin(t * 2 + i))
        draw.ellipse([px - 2, py - 2, px + 2, py + 2], fill=(80, 110, 160, p_alpha))

    # =============================================================
    # SCENE 1: The Dopamine Casino Phone (0.0s - 2.8s, frames 0-84)
    # =============================================================
    if f < 85:
        # Title with smooth slide down
        t_title = ease_out_cubic(min(1.0, t * 1.5))
        title_y = int(160 + (1.0 - t_title) * -50)
        draw.text((WIDTH // 2, title_y), "Cluttered home screens", font=FONT_TITLE, fill=(240, 240, 245), anchor="mm")
        draw.text((WIDTH // 2, title_y + 65), "scatter your daily focus.", font=FONT_SUBTITLE, fill=(239, 68, 68), anchor="mm")

        # Phone vibrating with relentless notifications
        shake_mag = 5 * min(1.0, t * 1.5)
        shake_x = int(shake_mag * math.sin(f * 1.2))
        shake_y = int(shake_mag * math.cos(f * 1.5))

        pw, ph = 740, 1380
        px = (WIDTH - pw) // 2 + shake_x
        py = 340 + shake_y

        sx, sy, sw, sh = draw_phone_frame(draw, px, py, pw, ph, glow_color=(80, 25, 25))

        # Inside screen: Chaotic app grid with neon badges
        apps_data = [
            ("Instagram", (225, 48, 108), "+99"),
            ("TikTok", (0, 242, 234), "99+"),
            ("YouTube", (255, 0, 0), "+14"),
            ("Snapchat", (255, 252, 0), "🔥 42"),
            ("X / Twitter", (29, 155, 240), "ALERT"),
            ("Discord", (88, 101, 242), "+38"),
        ]

        grid_cols, grid_rows = 2, 3
        col_w = sw // grid_cols
        row_h = 240

        for idx, (aname, acol, badge_txt) in enumerate(apps_data):
            c = idx % grid_cols
            r = idx // grid_cols
            ax = sx + c * col_w + 40
            ay = sy + 180 + r * row_h

            # Chaotic pulse
            bpulse = 1.0 + 0.08 * math.sin(f * 0.8 + idx * 2)
            icon_size = int(90 * bpulse)
            draw_rounded_rect(draw, [ax, ay, ax + icon_size, ay + icon_size], 24, fill=(acol[0], acol[1], acol[2], 230))

            # Red notification badge
            bx = ax + icon_size - 10
            by = ay - 10
            draw_rounded_rect(draw, [bx - 25, by, bx + 55, by + 40], 20, fill=(239, 68, 68))
            draw.text((bx + 15, by + 20), badge_txt, font=FONT_SMALL, fill=(255, 255, 255), anchor="mm")

            draw.text((ax + icon_size // 2, ay + icon_size + 30), aname, font=FONT_SMALL, fill=(180, 180, 190), anchor="mm")

        # Glitch / Flash into calm
        if f >= 80:
            flash_p = (f - 80) / 5.0
            draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(255, 255, 255, int(flash_p * 255)))

    # =============================================================
    # SCENE 2: The Antidote : Loom (2.8s - 6.5s, frames 85-195)
    # =============================================================
    elif f < 196:
        sf = f - 85
        st = sf / FPS

        # Header intro
        head_alpha = min(1.0, st * 2.0)
        draw.text((WIDTH // 2, 170), "Quietly learns how you use it.", font=FONT_TITLE, fill=(255, 255, 255), anchor="mm")
        draw.text((WIDTH // 2, 235), "Zero dopamine traps. Pure focus.", font=FONT_SUBTITLE, fill=(140, 150, 165), anchor="mm")

        # Phone mockup centered
        pw, ph = 740, 1400
        px = (WIDTH - pw) // 2
        py = 320
        sx, sy, sw, sh = draw_phone_frame(draw, px, py, pw, ph, glow_color=(40, 60, 90))

        # Time morphing demonstration
        is_morning = st < 1.9
        clock_text = "08:15 AM" if is_morning else "07:30 PM"
        context_tag = "MORNING ROUTINE" if is_morning else "EVENING WIND-DOWN"

        # Time pill inside phone screen
        draw_rounded_rect(draw, [sx + sw // 2 - 180, sy + 70, sx + sw // 2 + 180, sy + 150], 40, fill=(16, 18, 24), outline=(40, 45, 55), width=2)
        draw_clock_icon(draw, sx + sw // 2 - 105, sy + 110, r=16, color=(240, 240, 240))
        draw.text((sx + sw // 2 + 15, sy + 110), clock_text, font=FONT_BADGE, fill=(255, 255, 255), anchor="mm")

        draw.text((sx + sw // 2, sy + 180), context_tag, font=FONT_SMALL, fill=(90, 150, 255), anchor="mm")

        # Dynamic app items
        morning_apps = [
            ("Google Maps", "Commute"),
            ("Spotify", "Podcasts"),
            ("Gmail", "Work inbox"),
            ("Slack", "Team chats"),
            ("Calendar", "Daily agenda"),
        ]
        evening_apps = [
            ("WhatsApp", "Family"),
            ("Kindle", "Reading"),
            ("YouTube", "Watch later"),
            ("Messages", "SMS"),
            ("Phone", "Calls"),
        ]

        active_apps = morning_apps if is_morning else evening_apps

        # Transition effect between sets
        for i, (aname, asub) in enumerate(active_apps):
            slide_t = min(1.0, max(0.0, (st - 0.3 - i * 0.08) * 3.5))
            slide_x = (1.0 - ease_out_back(slide_t, s=1.2)) * 140
            item_y = sy + 230 + i * 160

            # Card container
            draw_rounded_rect(draw, [sx + 40 + slide_x, item_y, sx + sw - 40 + slide_x, item_y + 125], 24, fill=(12, 13, 17), outline=(32, 36, 45), width=2)
            # App rank
            draw.text((sx + 75 + slide_x, item_y + 62), f"{i + 1}", font=FONT_BODY_BOLD, fill=(100, 120, 160), anchor="lm")
            # App label
            draw.text((sx + 125 + slide_x, item_y + 48), aname, font=FONT_BODY_BOLD, fill=(240, 240, 240), anchor="lm")
            draw.text((sx + 125 + slide_x, item_y + 88), asub, font=FONT_SMALL, fill=(120, 125, 135), anchor="lm")
            # Chevron
            draw.text((sx + sw - 80 + slide_x, item_y + 62), ">", font=FONT_BODY, fill=(80, 85, 95), anchor="rm")

        # Bottom caption
        draw.text((WIDTH // 2, 1790), "Apps surface automatically when you need them.", font=FONT_SUBTITLE, fill=(100, 170, 255), anchor="mm")

    # =============================================================
    # SCENE 3: Real Proven Numbers (6.5s - 9.5s, frames 196-285)
    # =============================================================
    elif f < 286:
        sf = f - 196
        st = sf / FPS

        # Header
        draw.text((WIDTH // 2, 230), "Measured on 10,000+ real launches", font=FONT_SMALL, fill=(140, 150, 160), anchor="mm")
        draw.text((WIDTH // 2, 305), "Proven Real-World Data", font=FONT_HERO, fill=(255, 255, 255), anchor="mm")

        cards = [
            ("32%", "Already #1 on Screen", "Right app first try, 1 out of 3 times", (46, 164, 79), 0.1),
            ("83%", "Zero Search Needed", "Over 8 out of 10 opens immediately in view", (9, 105, 218), 0.35),
            ("< 1 ms", "100% On-Device ML", "Zero telemetry, zero battery waste, 0 internet", (137, 87, 229), 0.6),
        ]

        for i, (val, title, subtitle, accent_col, delay) in enumerate(cards):
            card_t = max(0.0, min(1.0, (st - delay) * 3.0))
            if card_t > 0:
                y_offset = (1.0 - ease_out_back(card_t, s=1.1)) * 100
                cy = 440 + i * 320 + y_offset

                # Glowing backdrop border
                box = [110, cy, WIDTH - 110, cy + 270]
                draw_rounded_rect(draw, [box[0] - 2, box[1] - 2, box[2] + 2, box[3] + 2], 34, outline=(accent_col[0], accent_col[1], accent_col[2], 90), width=4)
                draw_rounded_rect(draw, box, 32, fill=(14, 16, 22), outline=accent_col, width=2)

                # Big Stat with colored pill
                draw.text((170, cy + 90), val, font=FONT_CARD_NUM, fill=accent_col, anchor="lm")
                draw.text((170, cy + 170), title, font=FONT_TITLE, fill=(245, 245, 245), anchor="lm")
                draw.text((170, cy + 220), subtitle, font=FONT_SUBTITLE, fill=(160, 165, 175), anchor="lm")

        # Bottom guarantee
        draw_lock_icon(draw, WIDTH // 2 - 270, 1500, w=15, h=17, color=(74, 222, 128))
        draw.text((WIDTH // 2 + 15, 1500), "Zero network permissions in AndroidManifest.", font=FONT_SUBTITLE, fill=(74, 222, 128), anchor="mm")

    # =============================================================
    # SCENE 4: Outro & Call to Action (9.5s - 12.0s, frames 286-360)
    # =============================================================
    else:
        sf = f - 286
        st = sf / FPS

        # Breathing pulse on logo
        pulse = 1.0 + 0.04 * math.sin(st * 4)
        lsize = int(190 * pulse)
        if LOGO_IMG:
            rlogo = LOGO_IMG.resize((lsize, lsize), Image.Resampling.LANCZOS)
            img.paste(rlogo, (WIDTH // 2 - lsize // 2, 540 - lsize // 2), rlogo)

        draw.text((WIDTH // 2, 720), "Loom", font=FONT_HERO, fill=(255, 255, 255), anchor="mm")
        draw.text((WIDTH // 2, 800), "A quiet Android launcher.", font=FONT_TITLE, fill=(190, 195, 205), anchor="mm")

        # Highlights row
        draw.text((WIDTH // 2, 920), "100% Private  •  Under 1 MB  •  Apache 2.0", font=FONT_SUBTITLE, fill=(120, 160, 240), anchor="mm")

        # Button with light sweep reflection
        btn_y = 1050
        btn_box = [WIDTH // 2 - 340, btn_y, WIDTH // 2 + 340, btn_y + 130]
        draw_rounded_rect(draw, btn_box, 65, fill=(255, 255, 255))
        draw_download_icon(draw, WIDTH // 2 - 200, btn_y + 65, size=20, color=(0, 0, 0))
        draw.text((WIDTH // 2 + 30, btn_y + 65), "Download APK v1.3", font=FONT_BADGE, fill=(0, 0, 0), anchor="mm")

        # Light sweep gleam across button
        gleam_x = int(WIDTH // 2 - 340 + (st * 450) % 900)
        if btn_box[0] <= gleam_x <= btn_box[2] - 60:
            draw.line([gleam_x, btn_y + 10, gleam_x + 50, btn_y + 120], fill=(240, 240, 255, 120), width=8)

        # Footer
        draw.text((WIDTH // 2, 1290), "Early Alpha  •  Active Development  •  Vibe Coded", font=FONT_BODY, fill=(140, 145, 155), anchor="mm")
        draw.text((WIDTH // 2, 1360), "github.com/TidyMaze/loom", font=FONT_SUBTITLE, fill=(100, 180, 255), anchor="mm")

    return img.convert("RGB")

def main():
    print(f"🎬 Generating upgraded Loom motion design video ({TOTAL_FRAMES} frames, {DURATION_SEC}s)...")
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()

    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)

    cmd = [
        ffmpeg_exe,
        "-y",
        "-f", "rawvideo",
        "-vcodec", "rawvideo",
        "-s", f"{WIDTH}x{HEIGHT}",
        "-pix_fmt", "rgb24",
        "-r", str(FPS),
        "-i", "-",
        "-c:v", "libx264",
        "-pix_fmt", "yuv420p",
        "-preset", "fast",
        "-crf", "17",
        OUTPUT_PATH
    ]

    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)

    for frame_idx in range(TOTAL_FRAMES):
        if frame_idx % 60 == 0:
            print(f"  Rendering frame {frame_idx}/{TOTAL_FRAMES} ({(frame_idx / TOTAL_FRAMES) * 100:.0f}%)...")
        frame_img = render_frame(frame_idx)
        proc.stdin.write(frame_img.tobytes())

    proc.stdin.close()
    proc.wait()

    if proc.returncode != 0:
        err = proc.stderr.read().decode()
        print("❌ FFmpeg error:", err)
        sys.exit(1)

    size_mb = os.path.getsize(OUTPUT_PATH) / (1024 * 1024)
    print(f"✨ Upgraded video generated: {OUTPUT_PATH} ({size_mb:.2f} MB)")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Motion design promo video generator for Loom.
Renders high-quality 1080x1920 MP4 at 30 fps.
Uses Pillow for 2D graphic rendering and imageio_ffmpeg for H.264 encoding.
"""

import sys
import os
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg

WIDTH = 1080
HEIGHT = 1920
FPS = 30
DURATION_SEC = 11
TOTAL_FRAMES = FPS * DURATION_SEC

OUTPUT_PATH = os.path.abspath("docs/loom_promo.mp4")

# Load fonts
FONT_PATH = "/System/Library/Fonts/Helvetica.ttc"
if not os.path.exists(FONT_PATH):
    FONT_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"

def get_font(size, bold=False):
    try:
        # Helvetica.ttc index 1 is bold on macOS
        idx = 1 if bold else 0
        return ImageFont.truetype(FONT_PATH, size=size, index=idx)
    except Exception:
        return ImageFont.truetype(FONT_PATH, size=size)

FONT_HERO = get_font(90, bold=True)
FONT_TITLE = get_font(68, bold=True)
FONT_SUBTITLE = get_font(42, bold=False)
FONT_BODY = get_font(36, bold=False)
FONT_BADGE = get_font(48, bold=True)
FONT_SMALL = get_font(28, bold=False)

# Load Logo
LOGO_PATH = os.path.abspath("docs/app_icon_512.png")
LOGO_IMG = Image.open(LOGO_PATH).convert("RGBA") if os.path.exists(LOGO_PATH) else None

def ease_out_cubic(x):
    return 1 - pow(1 - x, 3)

def ease_in_out_quad(x):
    return 2 * x * x if x < 0.5 else 1 - pow(-2 * x + 2, 2) / 2

def draw_rounded_rect(draw, bbox, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(bbox, radius=radius, fill=fill, outline=outline, width=width)

def render_frame(f):
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 255))
    draw = ImageDraw.Draw(img)
    t = f / FPS

    # -------------------------------------------------------------
    # SCENE 1: The Problem (0s - 2.8s, frames 0-84)
    # -------------------------------------------------------------
    if f < 85:
        # Background: dark noisy slate
        bg_val = 14 + int(4 * math.sin(f * 0.5))
        draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(bg_val, bg_val + 2, bg_val + 6, 255))

        # Floating chaotic notification badges
        badges = [
            ("+24 Notifications", 180, 480, (239, 68, 68), 0.3),
            ("🔥 Trending right now!", 520, 680, (249, 115, 22), 0.6),
            ("⚠️ You're losing your streak!", 160, 1180, (234, 88, 12), 0.9),
            ("⚡ 8 people viewed your profile", 420, 1380, (239, 68, 68), 1.2),
            ("🔴 LIVE NOW", 680, 360, (220, 38, 38), 0.4),
        ]

        for text, bx, by, col, delay in badges:
            b_alpha = max(0.0, min(1.0, (t - delay) * 4))
            if b_alpha > 0:
                shake_x = math.sin(f * 0.8 + delay * 10) * 4
                shake_y = math.cos(f * 0.9 + delay * 10) * 3
                box = [bx + shake_x, by + shake_y, bx + shake_x + len(text) * 22 + 40, by + shake_y + 70]
                draw_rounded_rect(draw, box, 35, fill=(col[0], col[1], col[2], int(255 * b_alpha)))
                draw.text((bx + shake_x + 24, by + shake_y + 14), text, font=FONT_BODY, fill=(255, 255, 255))

        # Big central text
        t_prog = min(1.0, t / 1.2)
        y_pos = 900 - ease_out_cubic(t_prog) * 40
        draw.text((WIDTH // 2, y_pos), "Your phone is designed\nto steal your focus.",
                  font=FONT_TITLE, fill=(245, 245, 245), anchor="mm", align="center")

        # Glitch / Flash at end of scene
        if f >= 80:
            flash = (f - 80) / 5.0
            draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(255, 255, 255, int(flash * 255)))

    # -------------------------------------------------------------
    # SCENE 2: The Antidote: Loom (2.8s - 6.2s, frames 85-186)
    # -------------------------------------------------------------
    elif f < 187:
        sf = f - 85
        st = sf / FPS

        # Pure OLED black
        draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(0, 0, 0, 255))

        # Logo intro (scale + fade)
        logo_scale_prog = ease_out_cubic(min(1.0, st * 1.5))
        logo_size = int(140 * logo_scale_prog)

        if LOGO_IMG and logo_size > 10:
            resized_logo = LOGO_IMG.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
            img.paste(resized_logo, (WIDTH // 2 - logo_size // 2, 280 - logo_size // 2), resized_logo)

        # Title Loom
        if st > 0.4:
            alpha = min(1.0, (st - 0.4) * 3)
            col = int(255 * alpha)
            draw.text((WIDTH // 2, 420), "Loom", font=FONT_HERO, fill=(col, col, col), anchor="mm")
            draw.text((WIDTH // 2, 500), "A quiet Android launcher.", font=FONT_SUBTITLE, fill=(150, 150, 150), anchor="mm")

        # Interactive Demonstration of Habits changing
        if st > 1.0:
            demo_t = st - 1.0
            # Time indicator
            is_morning = demo_t < 1.3
            clock_text = "08:15 AM" if is_morning else "07:30 PM"
            mode_desc = "Morning commute" if is_morning else "Evening downtime"

            draw_rounded_rect(draw, [WIDTH // 2 - 200, 620, WIDTH // 2 + 200, 710], 45, fill=(20, 24, 30))
            draw.text((WIDTH // 2, 665), f"🕒 {clock_text}", font=FONT_BADGE, fill=(240, 240, 240), anchor="mm")
            draw.text((WIDTH // 2, 745), mode_desc, font=FONT_SMALL, fill=(130, 130, 140), anchor="mm")

            # App items
            morning_apps = ["1. Google Maps", "2. Spotify", "3. Gmail", "4. Slack", "5. Calendar"]
            evening_apps = ["1. WhatsApp", "2. YouTube", "3. Kindle", "4. Messages", "5. Phone"]

            apps = morning_apps if is_morning else evening_apps

            for idx, app_name in enumerate(apps):
                slide_in = min(1.0, max(0.0, (demo_t - 0.1 * idx) * 3))
                offset_x = (1.0 - ease_out_cubic(slide_in)) * 120
                item_y = 820 + idx * 130

                # App row container
                draw_rounded_rect(draw, [180 + offset_x, item_y, WIDTH - 180 + offset_x, item_y + 105],
                                  20, fill=(12, 14, 18), outline=(35, 38, 45), width=2)
                # App name
                draw.text((220 + offset_x, item_y + 52), app_name, font=FONT_BODY, fill=(230, 230, 230), anchor="lm")
                # Arrow
                draw.text((WIDTH - 240 + offset_x, item_y + 52), "→", font=FONT_BODY, fill=(100, 100, 110), anchor="rm")

            # Subtitle
            draw.text((WIDTH // 2, 1600), "Apps surface automatically when you need them.",
                      font=FONT_SUBTITLE, fill=(100, 180, 255), anchor="mm")

    # -------------------------------------------------------------
    # SCENE 3: Real Proven Numbers (6.2s - 9.0s, frames 187-270)
    # -------------------------------------------------------------
    elif f < 271:
        sf = f - 187
        st = sf / FPS

        draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(0, 0, 0, 255))

        draw.text((WIDTH // 2, 280), "Measured on 10,000+ launches", font=FONT_SMALL, fill=(130, 130, 130), anchor="mm")
        draw.text((WIDTH // 2, 360), "Real-World Efficiency", font=FONT_HERO, fill=(255, 255, 255), anchor="mm")

        cards = [
            ("32%", "Right app, first try", "Already #1 on screen 1 in 3 times", (46, 164, 79), 0.2),
            ("83%", "Zero search needed", "Immediately in direct view", (9, 105, 218), 0.5),
            ("< 1 ms", "100% On-Device ML", "Zero telemetry, zero battery waste", (137, 87, 229), 0.8),
        ]

        for i, (val, title, subtitle, accent_col, delay) in enumerate(cards):
            card_t = max(0.0, min(1.0, (st - delay) * 2.5))
            if card_t > 0:
                y_offset = (1.0 - ease_out_cubic(card_t)) * 80
                cy = 500 + i * 290 + y_offset

                # Card box
                box = [140, cy, WIDTH - 140, cy + 240]
                draw_rounded_rect(draw, box, 28, fill=(14, 16, 22), outline=accent_col, width=3)

                # Big Stat
                draw.text((200, cy + 85), val, font=FONT_HERO, fill=accent_col, anchor="lm")
                # Title & Subtitle
                draw.text((200, cy + 155), title, font=FONT_TITLE, fill=(240, 240, 240), anchor="lm")
                draw.text((200, cy + 200), subtitle, font=FONT_SMALL, fill=(150, 150, 160), anchor="lm")

        # Bottom guarantee
        draw.text((WIDTH // 2, 1520), "🔒 Zero internet permissions declared.",
                  font=FONT_SUBTITLE, fill=(74, 222, 128), anchor="mm")

    # -------------------------------------------------------------
    # SCENE 4: Outro & Call to Action (9.0s - 11.0s, frames 271-330)
    # -------------------------------------------------------------
    else:
        sf = f - 271
        st = sf / FPS

        draw.rectangle([(0, 0), (WIDTH, HEIGHT)], fill=(0, 0, 0, 255))

        # Pulse logo
        pulse = 1.0 + 0.05 * math.sin(st * 4)
        lsize = int(180 * pulse)
        if LOGO_IMG:
            rlogo = LOGO_IMG.resize((lsize, lsize), Image.Resampling.LANCZOS)
            img.paste(rlogo, (WIDTH // 2 - lsize // 2, 620 - lsize // 2), rlogo)

        draw.text((WIDTH // 2, 800), "Loom", font=FONT_HERO, fill=(255, 255, 255), anchor="mm")
        draw.text((WIDTH // 2, 880), "Minimalist. Offline. Free.", font=FONT_SUBTITLE, fill=(180, 180, 180), anchor="mm")

        # Download button
        btn_y = 1040
        btn_box = [WIDTH // 2 - 320, btn_y, WIDTH // 2 + 320, btn_y + 120]
        draw_rounded_rect(draw, btn_box, 60, fill=(255, 255, 255))
        draw.text((WIDTH // 2, btn_y + 60), "⬇ Download APK v1.3", font=FONT_BADGE, fill=(0, 0, 0), anchor="mm")

        # Details
        draw.text((WIDTH // 2, 1240), "Under 1 MB  •  Android 8.0+  •  Apache 2.0", font=FONT_BODY, fill=(140, 140, 140), anchor="mm")
        draw.text((WIDTH // 2, 1310), "github.com/TidyMaze/loom", font=FONT_SMALL, fill=(100, 160, 255), anchor="mm")

    return img.convert("RGB")

def main():
    print(f"🎬 Generating Loom motion design promo video ({TOTAL_FRAMES} frames, {DURATION_SEC}s)...")
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
        "-crf", "18",
        OUTPUT_PATH
    ]

    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)

    for frame_idx in range(TOTAL_FRAMES):
        if frame_idx % 30 == 0:
            print(f"  Rendering frame {frame_idx}/{TOTAL_FRAMES} ({(frame_idx/TOTAL_FRAMES)*100:.0f}%)...")
        frame_img = render_frame(frame_idx)
        proc.stdin.write(frame_img.tobytes())

    proc.stdin.close()
    proc.wait()

    if proc.returncode != 0:
        err = proc.stderr.read().decode()
        print("❌ FFmpeg error:", err)
        sys.exit(1)

    size_mb = os.path.getsize(OUTPUT_PATH) / (1024 * 1024)
    print(f"✅ Video generated successfully: {OUTPUT_PATH} ({size_mb:.2f} MB)")

if __name__ == "__main__":
    main()

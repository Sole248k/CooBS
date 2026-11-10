import os
from PIL import Image, ImageDraw

# Paths
root = r"d:\Fullstack\Capstone-dev"
public_dir = os.path.join(root, "frontend", "public")
logo_path = os.path.join(public_dir, "coobs-logo.png")
res_dir = os.path.join(root, "app", "android", "app", "src", "main", "res")

logo = Image.open(logo_path).convert("RGBA")

# 1. Generate master square brand badge (512x512)
size = 512
corner_radius = 110

img_master = Image.new("RGBA", (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(img_master)
# Crisp white squircle with subtle light border
draw.rounded_rectangle([(6, 6), (size-6, size-6)], radius=corner_radius, fill=(255, 255, 255, 255), outline=(226, 232, 240, 255), width=12)

# Scale CooBS logo cleanly
target_w = 420
scale = target_w / logo.width
target_h = int(logo.height * scale)
logo_resized = logo.resize((target_w, target_h), Image.Resampling.LANCZOS)

offset_x = (size - target_w) // 2
offset_y = (size - target_h) // 2
img_master.paste(logo_resized, (offset_x, offset_y), mask=logo_resized.split()[3])

# Save web favicon raster formats
img_master.save(os.path.join(public_dir, "apple-touch-icon.png"))
img_master.resize((192, 192), Image.Resampling.LANCZOS).save(os.path.join(public_dir, "favicon-192.png"))
img_master.resize((32, 32), Image.Resampling.LANCZOS).save(os.path.join(public_dir, "favicon.png"))
img_master.resize((32, 32), Image.Resampling.LANCZOS).save(os.path.join(public_dir, "favicon.ico"), format="ICO")

# 2. Generate Android app launcher icons
android_densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, icon_size in android_densities.items():
    target_folder = os.path.join(res_dir, folder)
    if os.path.exists(target_folder):
        icon = img_master.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        icon.save(os.path.join(target_folder, "ic_launcher.png"))
        print(f"Updated Android launcher icon: {folder}/ic_launcher.png ({icon_size}x{icon_size})")

# 3. Create SVG favicon with CooBS branding
svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="14" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="2"/>
  <text x="32" y="38" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', sans-serif" font-weight="900" font-size="22" text-anchor="middle" letter-spacing="-1">
    <tspan fill="#0F172A">Coo</tspan><tspan fill="#006699">BS</tspan>
  </text>
  <circle cx="32" cy="47" r="2.5" fill="#006699"/>
</svg>'''

with open(os.path.join(public_dir, "favicon.svg"), "w", encoding="utf-8") as f:
    f.write(svg_content)

print("Web favicons and Android launcher icons generated successfully!")

Add-Type -AssemblyName System.Drawing

# ==========================================
# 1. GENERATE 512x512 APP ICON
# ==========================================
$iconSize = 512
$iconBmp = [System.Drawing.Bitmap]::new($iconSize, $iconSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($iconBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Dark luxury background
$rect = [System.Drawing.Rectangle]::new(0, 0, $iconSize, $iconSize)
$bgBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    $rect,
    [System.Drawing.ColorTranslator]::FromHtml("#1A2232"),
    [System.Drawing.ColorTranslator]::FromHtml("#090C12"),
    45.0
)
$g.FillRectangle($bgBrush, $rect)

# Ambient subtle inner ring
$ringPen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#263248"), 4.0)
$g.DrawEllipse($ringPen, 24, 24, 464, 464)

# Draw Gold Shield
$shieldPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
$cx = 256.0
$topY = 90.0
$midY = 250.0
$botY = 425.0
$w = 145.0

$shieldPath.AddLine([float]($cx - $w), [float]$topY, [float]($cx + $w), [float]$topY)
$shieldPath.AddLine([float]($cx + $w), [float]$topY, [float]($cx + $w), [float]$midY)
# Bezier down to tip
$shieldPath.AddBezier(
    [float]($cx + $w), [float]$midY,
    [float]($cx + ($w * 0.75)), [float]($botY - 45),
    [float]($cx + ($w * 0.3)), [float]($botY - 15),
    [float]$cx, [float]$botY
)
# Bezier back up to left
$shieldPath.AddBezier(
    [float]$cx, [float]$botY,
    [float]($cx - ($w * 0.3)), [float]($botY - 15),
    [float]($cx - ($w * 0.75)), [float]($botY - 45),
    [float]($cx - $w), [float]$midY
)
$shieldPath.CloseFigure()

$shieldBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Rectangle]::new(100, 80, 312, 350),
    [System.Drawing.ColorTranslator]::FromHtml("#F59E0B"),
    [System.Drawing.ColorTranslator]::FromHtml("#B45309"),
    65.0
)
$g.FillPath($shieldBrush, $shieldPath)

$shieldBorderPen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#FDE68A"), 7.0)
$g.DrawPath($shieldBorderPen, $shieldPath)

# Inner Note Document
$notePath = [System.Drawing.Drawing2D.GraphicsPath]::new()
$nx = 196.0
$ny = 160.0
$nw = 120.0
$nh = 175.0
$fold = 36.0

$notePath.AddLine([float]$nx, [float]$ny, [float]($nx + $nw - $fold), [float]$ny)
$notePath.AddLine([float]($nx + $nw - $fold), [float]$ny, [float]($nx + $nw), [float]($ny + $fold))
$notePath.AddLine([float]($nx + $nw), [float]($ny + $fold), [float]($nx + $nw), [float]($ny + $nh))
$notePath.AddLine([float]($nx + $nw), [float]($ny + $nh), [float]$nx, [float]($ny + $nh))
$notePath.CloseFigure()

$noteBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#0F172A"))
$g.FillPath($noteBrush, $notePath)

# Fold corner
$foldPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
$foldPath.AddLine([float]($nx + $nw - $fold), [float]$ny, [float]($nx + $nw - $fold), [float]($ny + $fold))
$foldPath.AddLine([float]($nx + $nw - $fold), [float]($ny + $fold), [float]($nx + $nw), [float]($ny + $fold))
$foldPath.CloseFigure()
$foldBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#D97706"))
$g.FillPath($foldBrush, $foldPath)

# Keyhole Lock Icon
$goldKeyBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#FCD34D"))
$g.FillEllipse($goldKeyBrush, [float]243, [float]215, [float]26, [float]26)

$keyStem = [System.Drawing.Drawing2D.GraphicsPath]::new()
$keyStem.AddPolygon([System.Drawing.PointF[]]@(
    [System.Drawing.PointF]::new(249, 235),
    [System.Drawing.PointF]::new(263, 235),
    [System.Drawing.PointF]::new(267, 275),
    [System.Drawing.PointF]::new(245, 275)
))
$g.FillPath($goldKeyBrush, $keyStem)

# Secure note text lines
$linePen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#475569"), 4.0)
$g.DrawLine($linePen, [float]216, [float]300, [float]296, [float]300)

$iconPath = "D:\KotlinAps\CryptoNote\Release\store_icon_512x512.png"
$iconBmp.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$iconBmp.Dispose()
Write-Output "Successfully generated: $iconPath"


# ==========================================
# 2. GENERATE 1024x500 FEATURE GRAPHIC
# ==========================================
$bannerW = 1024
$bannerH = 500
$bannerBmp = [System.Drawing.Bitmap]::new($bannerW, $bannerH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$bg = [System.Drawing.Graphics]::FromImage($bannerBmp)
$bg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$bg.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

# Deep background
$bRect = [System.Drawing.Rectangle]::new(0, 0, $bannerW, $bannerH)
$bBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    $bRect,
    [System.Drawing.ColorTranslator]::FromHtml("#161F2E"),
    [System.Drawing.ColorTranslator]::FromHtml("#080B10"),
    30.0
)
$bg.FillRectangle($bBrush, $bRect)

# Ambient glow on the left
$glowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(35, 217, 155, 83))
$bg.FillEllipse($glowBrush, 60, 50, 480, 380)

# Shield Logo on Left (Mini version)
$sX = 90.0
$sY = 85.0
$sW = 35.0
$sH = 45.0
$bShield = [System.Drawing.Drawing2D.GraphicsPath]::new()
$bShield.AddLine([float]($sX - $sW), [float]$sY, [float]($sX + $sW), [float]$sY)
$bShield.AddLine([float]($sX + $sW), [float]$sY, [float]($sX + $sW), [float]($sY + 30))
$bShield.AddBezier(
    [float]($sX + $sW), [float]($sY + 30),
    [float]($sX + 20), [float]($sY + $sH + 15),
    [float]($sX + 10), [float]($sY + $sH + 25),
    [float]$sX, [float]($sY + $sH + 30)
)
$bShield.AddBezier(
    [float]$sX, [float]($sY + $sH + 30),
    [float]($sX - 10), [float]($sY + $sH + 25),
    [float]($sX - 20), [float]($sY + $sH + 15),
    [float]($sX - $sW), [float]($sY + 30)
)
$bShield.CloseFigure()

$bShieldBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Rectangle]::new(50, 80, 80, 90),
    [System.Drawing.ColorTranslator]::FromHtml("#F59E0B"),
    [System.Drawing.ColorTranslator]::FromHtml("#B45309"),
    45.0
)
$bg.FillPath($bShieldBrush, $bShield)
$bg.DrawPath([System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#FDE68A"), 3.0), $bShield)

# Brand Title & Fonts
$fontTitle = [System.Drawing.Font]::new("Segoe UI", [float]40, [System.Drawing.FontStyle]::Bold)
$fontSub = [System.Drawing.Font]::new("Segoe UI", [float]18, [System.Drawing.FontStyle]::Bold)
$fontDesc = [System.Drawing.Font]::new("Segoe UI", [float]13, [System.Drawing.FontStyle]::Regular)
$fontBadge = [System.Drawing.Font]::new("Segoe UI", [float]10.5, [System.Drawing.FontStyle]::Bold)

$brushWhite = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$brushGold = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#D99B53"))
$brushMuted = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#94A3B8"))

$bg.DrawString("CryptoNote", $fontTitle, $brushWhite, [float]145, [float]90)
$bg.DrawString("Zero-Knowledge Notes & Private Vault", $fontSub, $brushGold, [float]70, [float]195)
$bg.DrawString("Client-side AES-256-GCM • Argon2id Key Derivation • Offline-First Sync", $fontDesc, $brushMuted, [float]70, [float]240)

# Feature Badges
$badges = @("✓ E2EE Envelope V1", "✓ Hardware TEE Keystore", "✓ Dual-Zone Security", "✓ 100% Open Source")
$bx = 70.0
$by = 300.0
$badgeBg = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(40, 255, 255, 255))
$badgeBorder = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#334155"), 1.0)
$badgeText = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#E2E8F0"))

foreach ($badge in $badges) {
    $size = $bg.MeasureString($badge, $fontBadge)
    $bBox = [System.Drawing.RectangleF]::new($bx, $by, ($size.Width + 20), ($size.Height + 12))
    $bg.FillRectangle($badgeBg, $bBox)
    $bg.DrawRectangle($badgeBorder, $bBox.X, $bBox.Y, $bBox.Width, $bBox.Height)
    $bg.DrawString($badge, $fontBadge, $badgeText, [float]($bx + 10), [float]($by + 6))
    $bx += ($size.Width + 30)
    if ($bx -gt 520) {
        $bx = 70.0
        $by += 44.0
    }
}

# Right Side Card Mock
$cardX = 640.0
$cardY = 55.0
$cardW = 320.0
$cardH = 390.0
$cardRect = [System.Drawing.RectangleF]::new($cardX, $cardY, $cardW, $cardH)
$cardBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Rectangle]::new(640, 55, 320, 390),
    [System.Drawing.ColorTranslator]::FromHtml("#1E2738"),
    [System.Drawing.ColorTranslator]::FromHtml("#101622"),
    45.0
)
$bg.FillRectangle($cardBrush, $cardRect)
$bg.DrawRectangle([System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#334155"), 2.0), $cardRect.X, $cardRect.Y, $cardRect.Width, $cardRect.Height)

# Card header bar
$bg.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml("#242F44")), $cardX, $cardY, $cardW, 46)
$fontCardHeader = [System.Drawing.Font]::new("Segoe UI", [float]11, [System.Drawing.FontStyle]::Bold)
$bg.DrawString("🔒 SECURITY VAULT", $fontCardHeader, $brushGold, [float]($cardX + 20), [float]($cardY + 14))

# Card note lines
$cardLinePen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#2A374F"), 3.0)
$bg.DrawLine($cardLinePen, [float]($cardX + 24), [float]($cardY + 80), [float]($cardX + 260), [float]($cardY + 80))
$bg.DrawLine($cardLinePen, [float]($cardX + 24), [float]($cardY + 110), [float]($cardX + 290), [float]($cardY + 110))
$bg.DrawLine($cardLinePen, [float]($cardX + 24), [float]($cardY + 140), [float]($cardX + 210), [float]($cardY + 140))
$bg.DrawLine($cardLinePen, [float]($cardX + 24), [float]($cardY + 170), [float]($cardX + 275), [float]($cardY + 170))

# Biometric badge inside card
$badgeBox = [System.Drawing.RectangleF]::new(($cardX + 45), ($cardY + 230), 230, 110)
$bg.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(40, 217, 155, 83)), $badgeBox)
$bg.DrawRectangle([System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml("#D99B53"), 1.5), $badgeBox.X, $badgeBox.Y, $badgeBox.Width, $badgeBox.Height)

$fontBio = [System.Drawing.Font]::new("Segoe UI", [float]10, [System.Drawing.FontStyle]::Bold)
$fontBioSub = [System.Drawing.Font]::new("Segoe UI", [float]8.5, [System.Drawing.FontStyle]::Regular)
$bg.DrawString("BIOMETRIC LOCKED", $fontBio, $brushGold, [float]($cardX + 85), [float]($cardY + 255))
$bg.DrawString("Touch sensor to decrypt vault", $fontBioSub, $brushMuted, [float]($cardX + 80), [float]($cardY + 285))

$bannerPath = "D:\KotlinAps\CryptoNote\Release\store_feature_graphic_1024x500.png"
$bannerBmp.Save($bannerPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bg.Dispose()
$bannerBmp.Dispose()
Write-Output "Successfully generated: $bannerPath"

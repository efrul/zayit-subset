param([string]$Variant = 'sheet', [string]$Out = '')
Add-Type -AssemblyName System.Drawing
# Icon candidates for the app, drawn at 256 and scaled. -Variant A|B|C writes app.png/app.ico; 'sheet' writes a preview.
function C($hex, $a = 255) { $c = [Drawing.ColorTranslator]::FromHtml($hex); [Drawing.Color]::FromArgb($a, $c.R, $c.G, $c.B) }
function RRPath($x, $y, $w, $h, $r) { $p = New-Object Drawing.Drawing2D.GraphicsPath; $d = 2 * $r
  $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90); $p.CloseFigure(); $p }
function Grad($x, $y, $w, $h, $c1, $c2, $angle = 90) { New-Object Drawing.Drawing2D.LinearGradientBrush (New-Object Drawing.RectangleF $x, $y, $w, $h), $c1, $c2, $angle }
function Leaf($g, $brush, $cx, $cy, $len, $wid, $deg) {
  $st = $g.Save(); $g.TranslateTransform($cx, $cy); $g.RotateTransform($deg)
  $p = New-Object Drawing.Drawing2D.GraphicsPath
  $p.AddBezier(0, 0, $len * 0.3, -$wid, $len * 0.7, -$wid, $len, 0); $p.AddBezier($len, 0, $len * 0.7, $wid, $len * 0.3, $wid, 0, 0)
  $g.FillPath($brush, $p); $g.Restore($st) }
function Branch($g, $x, $y, $scale, $leafBrush, $stemColor) {
  $st = $g.Save(); $g.TranslateTransform($x, $y); $g.ScaleTransform($scale, $scale)
  $pen = New-Object Drawing.Pen $stemColor, 3; $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
  $g.DrawBezier($pen, 0, 40, 14, 26, 30, 14, 52, 0)
  Leaf $g $leafBrush 12 30 22 7 -125; Leaf $g $leafBrush 14 28 22 7 -20
  Leaf $g $leafBrush 26 19 22 7 -115; Leaf $g $leafBrush 28 17 22 7 -10
  Leaf $g $leafBrush 42 8 20 6 -95; Leaf $g $leafBrush 44 6 20 6 0
  $g.Restore($st) }
function Tile($g, $c1, $c2) { $p = RRPath 8 8 240 240 56; $g.FillPath((Grad 8 8 240 240 $c1 $c2), $p) }

function DrawA($g) {
  # A golden book cover on a deep olive tile, an olive branch embossed on it
  Tile $g (C '#4A6B26') (C '#22361A')
  $sh = RRPath 66 50 136 170 14; $g.FillPath((New-Object Drawing.SolidBrush (C '#000000' 60)), $sh)
  $pages = RRPath 70 42 132 168 12; $g.FillPath((Grad 70 42 132 168 (C '#FFF8E6') (C '#E9DCB8') 0), $pages)
  $cover = RRPath 58 38 130 168 12; $g.FillPath((Grad 58 38 130 168 (C '#F1CF6A') (C '#B8881C') 60), $cover)
  $g.FillRectangle((New-Object Drawing.SolidBrush (C '#8A6512' 120)), 72, 38, 6, 168)
  $frame = New-Object Drawing.Pen (C '#FFF1C2' 150), 3; $g.DrawPath($frame, (RRPath 88 58 84 128 8))
  Branch $g 104 92 1.35 (New-Object Drawing.SolidBrush (C '#5A4209' 200)) (C '#5A4209' 200)
}
function DrawB($g) {
  # A phone holding a shelf of golden books, on a cream tile with an olive frame
  Tile $g (C '#FBF4E2') (C '#EADBB4')
  $body = RRPath 70 22 116 212 22; $g.FillPath((Grad 70 22 116 212 (C '#3E5A22') (C '#22361A')), $body)
  $screen = RRPath 80 40 96 170 10; $g.FillPath((Grad 80 40 96 170 (C '#FFFDF6') (C '#F3E6C4')), $screen)
  $g.FillEllipse((New-Object Drawing.SolidBrush (C '#C9A227')), 120, 214, 16, 8)
  $g.FillPath((New-Object Drawing.SolidBrush (C '#C9A227')), (RRPath 90 104 16 76 4))
  $g.FillPath((New-Object Drawing.SolidBrush (C '#E2B94A')), (RRPath 109 94 16 86 4))
  $g.FillPath((New-Object Drawing.SolidBrush (C '#4A6B26')), (RRPath 128 112 14 68 4))
  $g.FillPath((New-Object Drawing.SolidBrush (C '#B8881C')), (RRPath 145 100 18 80 4))
  $g.FillRectangle((New-Object Drawing.SolidBrush (C '#8A6512')), 86, 180, 84, 6)
  Branch $g 96 50 0.95 (New-Object Drawing.SolidBrush (C '#4A6B26')) (C '#4A6B26')
}
function DrawC($g) {
  # A large book faded behind and a small golden one in front: the same library, smaller. Drawn mirrored, so the books
  # are Hebrew ones: the spine on the right, the page edges on the left.
  $g.TranslateTransform(256, 0); $g.ScaleTransform(-1, 1)
  Tile $g (C '#4A6B26') (C '#22361A')
  $back = RRPath 40 34 128 164 12; $g.FillPath((New-Object Drawing.SolidBrush (C '#F1CF6A' 70)), $back)
  $g.DrawPath((New-Object Drawing.Pen (C '#F1CF6A' 140), 3), $back)
  $sh = RRPath 116 102 98 124 12; $g.FillPath((New-Object Drawing.SolidBrush (C '#000000' 70)), $sh)
  $pages = RRPath 116 94 96 124 10; $g.FillPath((Grad 116 94 96 124 (C '#FFF8E6') (C '#E9DCB8') 0), $pages)
  $cover = RRPath 106 90 96 124 10; $g.FillPath((Grad 106 90 96 124 (C '#F1CF6A') (C '#B8881C') 60), $cover)
  $g.FillRectangle((New-Object Drawing.SolidBrush (C '#8A6512' 120)), 117, 90, 5, 124)
  Branch $g 132 124 0.95 (New-Object Drawing.SolidBrush (C '#5A4209' 200)) (C '#5A4209' 200)
}
function Render($v, $s) {
  $bmp = New-Object Drawing.Bitmap $s, $s; $g = [Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $g.PixelOffsetMode = 'HighQuality'; $g.InterpolationMode = 'HighQualityBicubic'
  $big = New-Object Drawing.Bitmap 256, 256; $gb = [Drawing.Graphics]::FromImage($big)
  $gb.SmoothingMode = 'AntiAlias'; $gb.PixelOffsetMode = 'HighQuality'
  & "Draw$v" $gb; $gb.Dispose()
  $g.DrawImage($big, 0, 0, $s, $s); $g.Dispose(); $big.Dispose(); $bmp }

if ($Variant -eq 'sheet') {
  $sheet = New-Object Drawing.Bitmap 900, 380; $g = [Drawing.Graphics]::FromImage($sheet); $g.Clear((C '#F3F3F3'))
  $font = New-Object Drawing.Font 'Segoe UI', 18, ([Drawing.FontStyle]::Bold)
  $i = 0
  foreach ($v in 'A', 'B', 'C') {
    $x = 30 + $i * 290
    $b = Render $v 256; $g.DrawImage($b, $x, 20); $b.Dispose()
    $s48 = Render $v 48; $g.DrawImage($s48, $x + 20, 300); $s48.Dispose()
    $s32 = Render $v 32; $g.DrawImage($s32, $x + 90, 308); $s32.Dispose()
    $s16 = Render $v 16; $g.DrawImage($s16, $x + 140, 316); $s16.Dispose()
    $g.DrawString($v, $font, [Drawing.Brushes]::Black, $x + 210, 305)
    $i++ }
  $g.Dispose(); $sheet.Save($Out); 'sheet'
} else {
  $dir = Join-Path $PSScriptRoot '..\src\main\resources\drawable'
  $sizes = 16, 20, 24, 32, 40, 48, 64, 256; $pngs = @()
  foreach ($s in $sizes) { $ms = New-Object IO.MemoryStream; $b = Render $Variant $s; $b.Save($ms, [Drawing.Imaging.ImageFormat]::Png); if ($s -eq 256) { $b.Save("$dir\app.png") }; $pngs += , ($ms.ToArray()); $b.Dispose() }
  $o = New-Object IO.MemoryStream; $bw = New-Object IO.BinaryWriter $o
  $bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]$sizes.Count); $off = 6 + 16 * $sizes.Count
  for ($i = 0; $i -lt $sizes.Count; $i++) { $s = $sizes[$i]; $v = if ($s -ge 256) { 0 } else { $s }
    $bw.Write([byte]$v); $bw.Write([byte]$v); $bw.Write([byte]0); $bw.Write([byte]0); $bw.Write([UInt16]1); $bw.Write([UInt16]32); $bw.Write([UInt32]$pngs[$i].Length); $bw.Write([UInt32]$off); $off += $pngs[$i].Length }
  foreach ($p in $pngs) { $bw.Write($p) }; $bw.Flush(); [IO.File]::WriteAllBytes("$dir\app.ico", $o.ToArray()); "icon $Variant"
}

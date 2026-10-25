# Design Review Workflow (/design-review)

This workflow is executed to audit any screen or UI change against the design system and external design skills.

## Step 1: Capture Multi-Condition Screencaps
Run on the active Android emulator:
```powershell
$adb = "C:\Users\User\Android\Sdk\platform-tools\adb.exe"
# Capture screen in target state
& $adb shell screencap -p /sdcard/review_shot.png
& $adb pull /sdcard/review_shot.png "shots/<screen>_<theme>_<lang>_<scale>.png"
```

## Step 2: Visual Inspection Against Rubric
Audit against the 12-point rubric:
1. **Hierarchy**: One clear primary action per screen.
2. **Grid & Alignment**: Strict adherence to 4/8/12/16/24/32dp spacing scale.
3. **Typography**: No clipped text, no awkward wrapping, survives long German (`de`) and dense CJK (`zh-CN`).
4. **Contrast**: WCAG AA compliance (>= 4.5:1 text, >= 3:1 graphical components).
5. **Touch Targets**: All interactive elements >= 48dp.
6. **Density**: Information-dense lists without empty dead spaces.
7. **Action Reachability**: Primary actions reachable without scrolling (sticky headers/FAB/pinned bars).
8. **Designed States**: Empty, loading, error, and timeout states designed and illustrated with vectors.
9. **Motion**: Smooth, purposeful transitions (150-300ms) honoring reduce-motion.
10. **Restraint**: Neon, glass, and blur used sparingly without hurting readability.
11. **State Legibility**: Connected vs Disconnected is unmistakable in < 500ms glance.
12. **Theme Consistency**: Distinct, non-templated look across OLED Crimson, Cyber Violet, and Monochrome.

## Step 3: Squint & Empty State Checks
- **Squint Test**: Blur the image or view at low resolution — does the primary action and state remain obvious?
- **Empty State Test**: Fresh install with zero profiles — does the screen guide the user with clear instructions?

## Step 4: Iterative Remediation
If any rubric item fails, make the targeted Compose fix and repeat the capture loop until all points pass.

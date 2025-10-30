# Journey Mode v1.2.0 - Smart Thresholds & Submit Button

**A Minecraft mod for NeoForge 1.21.1 that unlocks unlimited item access after collecting enough of them**

## ✨ What's New in v1.2.0

### Major UI/UX Improvements
- 🔘 **Submit Button**: Items no longer auto-deposit - click "Submit" button to confirm
  - Green button appears when item is in deposit slot
  - Prevents accidental deposits
- 📊 **Live Item Info**: See requirements before submitting:
  - Required threshold for that specific item
  - Current collected count vs. required
  - Progress percentage
  - "Already Unlocked!" indicator for unlocked items
- 🚫 **Unlocked Item Protection**: Cannot deposit items that are already unlocked
- 🎯 **Better Layout**: Taller GUI window to prevent text overlap

### Dynamic Thresholds (from v1.1.0)
- **Stack size 1 items** (tools, armor): Require only **1 item**
- **Raw materials** (ores, wood): Require **full stack size** (64 for most)
- **Crafted items (Depth 1)**: Require **50% of stack size** (32 for most)
- **Crafted items (Depth 2)**: Require **25% of stack size** (16 for most)
- **Complex items (Depth 3+)**: Require only **1 item**

## 🐛 Bug Fixes
- Fixed shift-click crash when removing items from deposit slot
- Fixed progress percentage overlapping with inventory label
- Fixed text overlap issues in GUI

## 📦 Installation
1. Download `journeymode-1.2.0.jar` from the release assets
2. Place in your `.minecraft/mods` folder
3. Launch Minecraft 1.21.1 with NeoForge 21.1.72+

## 🎮 How to Use
1. Press **J** to open Journey Mode menu
2. Place items in deposit slot
3. See required threshold and current progress
4. Click **Submit** button to deposit
5. Once unlocked, switch to Journey tab to retrieve items infinitely!

## 📋 Requirements
- Minecraft 1.21.1
- NeoForge 21.1.72 or later
- Java 21 or later

---

**Full Changelog**: https://github.com/Aryangpt007/Journey-Mode#-changelog

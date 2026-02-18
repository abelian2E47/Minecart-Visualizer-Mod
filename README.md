# Minecart-Visualizer-Mod
A comprehensive Minecraft client-side mod that provides advanced visualization and tracking tools for minecart systems.

## ⌨️ Hotkeys
* **C + V**: Open the configuration screen (Requires **YetAnotherConfigLib**).

## 🛠 Features

### 🛰️ HopperMinecartTracker
Real-time monitoring of Hopper Minecarts with detailed chat feedback:
* **Inventory Changes**: Receive notifications whenever items are added or removed from a minecart.
* **Destruction Alerts**: Get notified immediately when a minecart is destroyed or removed from the world.
* **Optimization**: Completely refactored codebase for 1.21.x rendering architecture, fixing potential memory leaks and crash bugs during large-scale minecart stacking.

### 🔍 Advanced Filtering
Control exactly what information reaches your chat bar. The Filter system supports multi-color tracking:
* **WhiteList**: Only items in the whitelist will trigger chat notifications.
* **BlackList**: Items in the blacklist will be ignored by the tracker.

### 📊 Item Counter
Designed for technical players to measure system efficiency:
* Tracks the total throughput of items for specific tracker colors over a period of time.
* Useful for calculating item-per-hour (IPH) rates in sorting systems or farms.

---

## 💻 Command Reference
The root command is `/MinecartVisualizer`. Running it without arguments toggles the main visualization on/off.

### 1. Settings (`/MinecartVisualizer setting`)
Quickly toggle rendering options:
* `InfoTextDisplay <true/false>`: Toggle the text overlay above minecarts.
* `AlwaysFacingThePlayer <true/false>`: Toggle whether the info text rotates to face you.
* `MergeStackingMinecartInfo <true/false>`: Consolidate info displays for stacked minecarts.

### 2. Filter Logic (`/MinecartVisualizer filter <color> <white/black>`)
Configure item filtering for specific tracker colors:
* **Sub-commands**:
    * `add <item>`: Add a specific item to the list.
    * `add hand`: Add the item currently in your main hand to the list.
    * `remove <item/hand>`: Remove the specified item from the list.
    * `clear`: Wipe the entire list for that color/type.
    * `list`: Display all items currently in the specified filter.

### 3. Counters (`/MinecartVisualizer counter <color>`)
Manage statistical data collection:
* `reset`: Clear all counted data for the specified color.
* `print`: Output a detailed report of collected item counts to the chat.
* Execute the base command `/MinecartVisualizer counter <color>` to toggle the counter state.

### 4. Point Management (`/MinecartVisualizer point`)
Mark specific world coordinates for targeted tracking:
* `add <color> <x> <y> <z>`: Add a tracking point at the specified coordinates.
* `add <color> look`: Add a tracking point at the block you are currently looking at.
* `remove <pos/color>`: Remove points by specific coordinate string or clear an entire color group.
* `list`: Show all registered tracking points.
* `clear`: Remove all points from the system.

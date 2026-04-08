# Copy Book

A Meteor Client addon for **Minecraft 1.21.8** that copies a book's pages to empty Books and Quills in your inventory.

## Commands

### `.copybook <copies> [title]`

Copies the pages of a held book into the specified number of empty Books and Quills in your inventory, signing each one.

**Arguments:**

| Argument | Required | Description |
|----------|----------|-------------|
| `copies` | Yes | Number of copies to make (must be at least 1) |
| `title` | No | Title for the signed books (max 15 characters). If omitted, uses the title of the held Written Book. |

**Usage:**

- Hold a **Written Book** and run `.copybook <copies>` — uses the existing title automatically.
- Hold a **Written Book** or **Book and Quill** and run `.copybook <copies> <title>` — signs each copy with the given title.

**Examples:**

```
.copybook 5
.copybook 3 My Book
```

**Requirements:**

- You must be holding a Written Book or Book and Quill in your main hand.
- Your inventory must contain at least `<copies>` empty Books and Quills (books with no pages written).

**Notes:**

- The command waits 1–2 seconds between each copy to avoid detection.
- Sneak (hold Shift) at any time during the process to cancel.
- Progress is shown in chat (e.g. `1/5`, `2/5`, ...).

## Installation

Check the [Releases](../../releases) page for pre-built JAR files. Place the JAR alongside Meteor Client in your `mods` folder.

## Building

Run the Gradle `build` task to produce a JAR in `build/libs/`. Place it alongside Meteor Client in your `mods` folder.

## License

This project is available under the CC0 license.

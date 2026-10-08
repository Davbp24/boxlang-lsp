# Running a Local Build of the LSP in VS Code

How to build this language server, start it, and use it from VS Code instead of the released server.

## Step 1: Package the server

From the `boxlang-lsp` folder:

```bash
./gradlew downloadBoxLang   # first time only: downloads the BoxLang runtime JAR the build needs
./gradlew shadowJar
```

`shadowJar` does three things:

1. **Compile:** turns the `.java` files into `.class` files that Java can run.
2. **Pack:** puts your classes, plus the libraries the server uses (lsp4j and others), into one file: `build/libs/bx-lsp-<version>-all.jar`.
3. **Make it a BoxLang module:** copies the JAR plus `box.json` and `ModuleConfig.bx` into `build/module/` (see `createModuleStructure` in `build.gradle`).

The server isn't a standalone program. It's a **plugin (module) for BoxLang**, so it has to be packaged the way BoxLang expects.

## Step 2: Start the server ("Run LSP")

1. Open the `boxlang-lsp` folder itself in VS Code (File → Open Folder). VS Code only reads `.vscode/launch.json` from the folder you have open.
2. Install **Extension Pack for Java** (one time).
3. Open Run and Debug (⇧⌘D), pick **"Run LSP"**, and press ▶ (or F5).

The Java extension reads the config in `boxlang-lsp/.vscode/launch.json`. It turns each field into part of a command, pastes that command into the terminal and runs it:

| Part of the command | Came from (`launch.json`) | What it does |
|---|---|---|
| (terminal starts in) `test-bx-project` | `"cwd"` | The folder the server starts in |
| `BOXLANG_HOME=.../.boxlang_home` | `"env"` | BoxLang's own working folder, kept inside the project |
| `xBOXLANG_CONFIG=boxlang.json` | `"env"` | Switched off on purpose (the `x` in front means BoxLang ignores it) |
| `BOXLANG_MODULESDIRECTORY=.../build` | `"env"` | Where BoxLang looks for plugins, i.e. where `shadowJar` put the server |
| `/Library/.../jdk-21.jdk/.../bin/java` | `"type": "java"` | Your installed Java 21, used to run everything |
| `-agentlib:jdwp=...` | `"type": "java"` + the debug button | Connects VS Code's debugger, so breakpoints work |
| `@/var/folders/.../cp_….argfile` | `"projectName"` | Temporary file listing every place code lives (too long to fit on one command) |
| `ortus.boxlang.runtime.BoxRunner` | `"mainClass"` | The program to start: **BoxLang itself** |
| `module:bx-lsp` | `"args"` | Tells BoxLang to run the bx-lsp plugin, which starts the server (`App`) |
| `--debug-server-port 7777` | `"args"` | Tells the server which "door number" to listen on (`CLI.java`) |

**The chain from click to running server:**

```text
You click ▶ "Run LSP"
   → Java extension reads launch.json
   → builds the command (table above) and runs it in the terminal
   → Java starts BoxLang (BoxRunner)
   → BoxLang finds the plugin in build/ and runs it (module:bx-lsp)
   → the plugin starts the server (App)
   → the server opens port 7777 and waits for an editor to connect
```

It's working when the terminal shows:

```text
Starting debug server on port 7777
[LSP] Listening on port: 7777
[LSP] Bound on: /127.0.0.1
waiting for a connection
```

`127.0.0.1` means only your own computer can connect. Leave this running.

## Step 3: Connect the extension to your server ("Launch Extension")

### Before you start

- **"Run LSP" must already be running** in the `boxlang-lsp` window, showing `waiting for a connection`.
- **Node.js must be installed.** The extension is written in TypeScript, and Node builds it. Check in a terminal:

  ```bash
  node -v
  npm -v
  ```

  If either says "command not found", install Node from nodejs.org first.

### Steps

1. **Open the extension's folder in a new window.** File → New Window, then File → Open Folder… and choose `vscode-boxlang`.
2. **Install its dependencies (first time only).** In that window's terminal:

   ```bash
   npm install
   ```

3. **Switch on the port setting.** Open `vscode-boxlang/.vscode/launch.json` and find:

   ```json
   "xBOXLANG_LSP_PORT": "7777",
   ```

   Delete the `x` so it reads:

   ```json
   "BOXLANG_LSP_PORT": "7777",
   ```

   Save. This tells the extension to connect to your server on port 7777 instead of starting its own.
4. **Launch.** Open Run and Debug (⇧⌘D), choose **"Launch Extension"** in the dropdown at the top, and press ▶ (or F5).
5. **Wait for it to build.** Before launching, VS Code runs a task called `npm: watch`, which compiles the extension's code. The first time can take a minute. A terminal stays open running this task. Leave it running, since it rebuilds the extension when the extension's code changes.
6. **The new window opens.** A new VS Code window appears with **[Extension Development Host]** in its title bar. That's the window you test in.

Because `BOXLANG_LSP_PORT` is set, the extension in that window connects to your server on port 7777 instead of starting the released one (`src/utils/LanguageServer.ts`, `getLSPServerConfig`).

### Checking that it connected

1. In the Extension Development Host window, use File → Open Folder and open something with BoxLang files, for example `boxlang-lsp/src/test/resources/test-bx-project`.
2. Open any `.bx` file. The extension only connects once a BoxLang file is open.
3. Check two places:
   - **The `boxlang-lsp` window's terminal** (your server) should print new log lines after `waiting for a connection`. That's the extension connecting.
   - **In the Extension Development Host:** View → Output, then choose the BoxLang channel from the dropdown. You should see messages about connecting to port 7777.

### If something goes wrong

- **The window opens but nothing connects:** make sure "Run LSP" is still running, and that you removed the `x` in step 3 and saved.
- **The build fails in step 5:** read the error in the `npm: watch` terminal.

The `launch.json` change in `vscode-boxlang` is a local edit. Don't commit it unless your team wants it.

## Where to test

Test **only in the [Extension Development Host] window**. That's the only window connected to your local server.

- The `boxlang-lsp` window and the `vscode-boxlang` window still use the normally installed BoxLang extension, which runs the **released** server. Your changes don't show up there.

In the Extension Development Host window, use File → Open Folder to open any folder with BoxLang files (`.bx`, `.bxs`, `.bxm`, `.cfc`, `.cfm`).

Good options already in this repo:

- `boxlang-lsp/src/test/resources/test-bx-project/` is a small sample project (`Main.bx`, `Car.bx`, `CarChild.bx`, …).
- `boxlang-lsp/src/test/resources/files/` holds the files the unit tests use, for example `hoverTestClass.bx`.

Don't save changes to files under `src/test/resources/`. Unit tests depend on their exact contents and line/column positions.

## Using Expand Selection

Put your cursor on a word in a BoxLang file, then use any of these:

- **Shortcut (Mac):** hold **Control + Shift + Command** and press the **right arrow** (⌃⇧⌘→). Each press grows the selection one step. The **left arrow** (⌃⇧⌘←) shrinks it back.
- **Menu bar:** Selection → **Expand Selection** (and **Shrink Selection**)
- **Command Palette:** press **Command + Shift + P**, type `Expand Selection`, and press Enter

| Symbol | Key |
|---|---|
| ⌃ | Control |
| ⇧ | Shift |
| ⌘ | Command |
| → / ← | Right / left arrow |

VS Code only asks the server for selection ranges (`textDocument/selectionRange`) if the server announces at startup that it supports them. Until it does, VS Code falls back to its own built-in version, which only understands words and brackets. In that case, no request reaches the server.

## The server isn't tied to `test-bx-project`

`"cwd"` is only the folder the server *starts in*. The editor tells the server which project to work on after it connects:

1. When VS Code connects, the first message it sends includes the folder you have open.
2. Each time you open a file, VS Code sends that file's code to the server (`textDocument/didOpen`).

So the server works on whatever folder you open in the Extension Development Host window.

## Seeing the messages (trace)

Add this to the settings of the folder you open in the Extension Development Host:

```json
"boxlang.trace.server": "verbose"
```

The JSON messages between VS Code and the server then show up in the Output panel, in the BoxLang channel.

`boxlang.lsp.logLevel` is a different setting: it controls the server's own log messages, not the JSON traffic.

## After changing server code

The running server doesn't pick up code changes by itself:

1. Stop "Run LSP" (red square).
2. Run `./gradlew shadowJar` again.
3. Start "Run LSP" again.
4. In the Extension Development Host window, reload (⌘R) so the extension reconnects.

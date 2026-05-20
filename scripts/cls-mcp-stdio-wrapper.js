const { spawn } = require("child_process");

const isWindows = process.platform === "win32";
const packageName = process.env.CLS_MCP_SERVER_PACKAGE || "cls-mcp-server@latest";
const command = isWindows ? "cmd.exe" : "npx";
const args = isWindows ? ["/c", "npx", "-y", packageName] : ["-y", packageName];

const child = spawn(command, args, {
  env: {
    ...process.env,
    TRANSPORT: process.env.TRANSPORT || "stdio",
  },
  stdio: ["pipe", "pipe", "pipe"],
});

process.stdin.pipe(child.stdin);
child.stderr.pipe(process.stderr);

let stdoutBuffer = "";

function forwardStdoutLine(line) {
  const text = line.replace(/\r?\n$/, "");
  if (text === "Started cls-mcp-server in stdio transport.") {
    return;
  }
  process.stdout.write(line);
}

child.stdout.on("data", (chunk) => {
  stdoutBuffer += chunk.toString();

  let newlineIndex;
  while ((newlineIndex = stdoutBuffer.search(/\r?\n/)) >= 0) {
    const line = stdoutBuffer.slice(0, newlineIndex + (stdoutBuffer[newlineIndex] === "\r" ? 2 : 1));
    stdoutBuffer = stdoutBuffer.slice(line.length);
    forwardStdoutLine(line);
  }
});

child.on("close", (code, signal) => {
  if (stdoutBuffer) {
    forwardStdoutLine(stdoutBuffer);
  }

  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 0);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    child.kill(signal);
  });
}

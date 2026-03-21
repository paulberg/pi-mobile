import readline from "node:readline";

const stdinReader = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});

stdinReader.on("line", (line) => {
  const command = JSON.parse(line);

  process.stdout.write(
    `${JSON.stringify({
      id: command.id,
      type: "response",
      command: command.type ?? "unknown",
      success: true,
      data: {
        text: "before\u2028middle\u2029after",
      },
    })}\n`,
  );
});

process.on("SIGTERM", () => {
  process.exit(0);
});

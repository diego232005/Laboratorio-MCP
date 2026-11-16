import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const jar = path.resolve(__dirname, "../target/mcp-server-tdea-0.0.1-SNAPSHOT.jar");

const transport = new StdioClientTransport({
  command: "java",
  args: ["-jar", jar],
});

const client = new Client({ name: "smoke-test", version: "1.0.0" });

async function call(name, args = {}) {
  const result = await client.callTool({ name, arguments: args });
  const text = result.content?.map((c) => c.text).join("\n") ?? JSON.stringify(result);
  console.log(`\n>>> ${name}`);
  console.log(text);
  return text;
}

await client.connect(transport);

const tools = await client.listTools();
console.log("TOOLS:", tools.tools.map((t) => t.name).join(", "));

const created1 = await call("createAccount", {
  ownerName: "Ana Perez",
  accountType: "AHORROS",
  initialBalance: 500000,
});
const created2 = await call("createAccount", {
  ownerName: "Luis Gomez",
  accountType: "CORRIENTE",
  initialBalance: 200000,
});

const acc1 = created1.match(/ACC-\d+/)?.[0];
const acc2 = created2.match(/ACC-\d+/)?.[0];
if (!acc1 || !acc2) throw new Error("No se pudieron crear cuentas");

await call("deposit", { accountNumber: acc1, amount: 50000, description: "Nomina" });
await call("transfer", {
  fromAccount: acc1,
  toAccount: acc2,
  amount: 25000,
  description: "Pago alquiler",
});
const credit = await call("createCreditRequest", {
  accountNumber: acc1,
  amount: 1000000,
  termMonths: 12,
  purpose: "Remodelacion",
});
const requestId = Number(credit.match(/#(\d+)/)?.[1]);
if (!requestId) throw new Error("No se pudo crear solicitud de credito");
await call("listCreditRequests", { accountNumber: "" });
await call("approveCreditRequest", { requestId });
await call("getAccount", { accountNumber: acc1 });
await call("getTransactionHistory", { accountNumber: acc1 });
await call("listAccounts", {});


await client.close();
console.log("\nSMOKE TEST OK");

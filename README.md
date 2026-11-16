# Laboratorio MCP TDEA — Servidor Bancario

Servidor MCP con Spring Boot + Spring AI + H2 que expone herramientas bancarias a Claude Desktop.

## Requisitos

- Java 17+
- Maven 3.6+
- Claude Desktop (opcional para prueba con lenguaje natural)

## Compilar

```powershell
cd C:\Users\Diego\Downloads\MCP\mcp-server-tdea
mvn clean package -DskipTests
```

JAR: `target\mcp-server-tdea-0.0.1-SNAPSHOT.jar`

## Herramientas MCP

| Tool | Descripción |
|------|-------------|
| `createAccount` | Crear cuenta (AHORROS/CORRIENTE) |
| `getAccount` | Consultar cuenta |
| `listAccounts` | Listar cuentas |
| `deposit` | Consignación |
| `withdraw` | Retiro |
| `transfer` | Transferencia entre cuentas |
| `createCreditRequest` | Solicitud de crédito |
| `approveCreditRequest` | Aprobar y desembolsar crédito |
| `rejectCreditRequest` | Rechazar crédito |
| `listCreditRequests` | Listar solicitudes |
| `getTransactionHistory` | Historial de movimientos |

BD H2 y logs: `%USERPROFILE%\mcp-banking\`

## Claude Desktop

1. Copia/fusiona `claude_desktop_config.example.json` en:
   `%APPDATA%\Claude\claude_desktop_config.json`
2. Reinicia Claude Desktop.
3. Prueba: *"Crea una cuenta de ahorros para Ana con saldo 500000"*

## MCP Inspector (stdio)

```powershell
npx @modelcontextprotocol/inspector java -jar "C:\Users\Diego\Downloads\MCP\mcp-server-tdea\target\mcp-server-tdea-0.0.1-SNAPSHOT.jar"
```

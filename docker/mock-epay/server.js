const http = require("http");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 18080);
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || `http://localhost:${PORT}`;
const MERCHANT_PID = process.env.MERCHANT_PID || "1000001";
const MERCHANT_KEY = process.env.MERCHANT_KEY || "demo-epay-key";

const ordersByTradeNo = new Map();
const tradeNoByOrderId = new Map();

function md5(input) {
  return crypto.createHash("md5").update(input, "utf8").digest("hex");
}

function buildSign(params, merchantKey) {
  const pairs = Object.entries(params)
    .filter(([key, value]) => key !== "sign" && key !== "sign_type" && value !== undefined && value !== null && value !== "")
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`);
  return md5(`${pairs.join("&")}${merchantKey}`);
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function sendHtml(res, statusCode, html) {
  res.writeHead(statusCode, { "Content-Type": "text/html; charset=utf-8" });
  res.end(html);
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      try {
        resolve(Buffer.concat(chunks).toString("utf8"));
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function createTradeNo() {
  return `MOCK${Date.now()}${Math.random().toString().slice(2, 8)}`;
}

function renderPayPage(order, message = "") {
  const statusText = order.status === "PAID" ? "已支付" : "待支付";
  const statusColor = order.status === "PAID" ? "#15803d" : "#1d4ed8";
  const actionHtml = order.status === "PAID"
    ? `<a class="primary" href="${escapeHtml(order.returnUrl)}">返回订单页</a>`
    : `
      <form method="POST" action="/complete">
        <input type="hidden" name="trade_no" value="${escapeHtml(order.tradeNo)}" />
        <button class="primary" type="submit">模拟支付成功</button>
      </form>
    `;

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>本地模拟易支付</title>
  <style>
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: linear-gradient(180deg, #eff6ff, #ffffff); color: #0f172a; }
    .wrap { max-width: 560px; margin: 0 auto; padding: 40px 20px; }
    .card { background: #fff; border: 1px solid #dbeafe; border-radius: 20px; padding: 28px; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08); }
    .badge { display: inline-flex; align-items: center; padding: 6px 12px; border-radius: 999px; background: #dbeafe; color: #1d4ed8; font-size: 13px; font-weight: 600; }
    .grid { display: grid; gap: 14px; margin: 24px 0; }
    .row { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid #e2e8f0; }
    .label { color: #64748b; font-size: 14px; }
    .value { font-weight: 600; word-break: break-all; text-align: right; }
    .status { color: ${statusColor}; }
    .hint { margin-top: 16px; font-size: 14px; color: #475569; line-height: 1.6; }
    .notice { margin: 16px 0 0; padding: 12px 14px; border-radius: 14px; background: #f8fafc; color: #334155; font-size: 14px; }
    .actions { display: flex; gap: 12px; margin-top: 24px; }
    .primary, .secondary, button { appearance: none; border: 0; border-radius: 12px; padding: 12px 18px; font-size: 15px; font-weight: 600; text-decoration: none; cursor: pointer; }
    .primary, button { background: #2563eb; color: #fff; }
    .secondary { background: #e2e8f0; color: #0f172a; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <span class="badge">本地模拟易支付</span>
      <h1>本地测试收银台</h1>
      <p class="hint">这个页面只用于本地开发测试，不会真实扣款。点击下方按钮后，会自动回调你的本地项目并把订单标记为已支付。</p>
      ${message ? `<div class="notice">${escapeHtml(message)}</div>` : ""}
      <div class="grid">
        <div class="row"><span class="label">订单号</span><span class="value">${escapeHtml(order.outTradeNo)}</span></div>
        <div class="row"><span class="label">网关单号</span><span class="value">${escapeHtml(order.tradeNo)}</span></div>
        <div class="row"><span class="label">商品名称</span><span class="value">${escapeHtml(order.name)}</span></div>
        <div class="row"><span class="label">支付方式</span><span class="value">${escapeHtml(order.type)}</span></div>
        <div class="row"><span class="label">支付金额</span><span class="value">${escapeHtml(order.money)}</span></div>
        <div class="row"><span class="label">订单状态</span><span class="value status">${statusText}</span></div>
      </div>
      <div class="actions">
        ${actionHtml}
        <a class="secondary" href="${escapeHtml(order.returnUrl)}">返回订单查询</a>
      </div>
    </div>
  </div>
</body>
</html>`;
}

function escapeHtml(input) {
  return String(input)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function notifyOrderPaid(order) {
  const params = {
    money: order.money,
    out_trade_no: order.outTradeNo,
    trade_no: order.tradeNo,
    trade_status: "TRADE_SUCCESS",
  };
  params.sign = buildSign(params, MERCHANT_KEY);
  params.sign_type = "MD5";

  const callbackUrl = `${order.notifyUrl}${order.notifyUrl.includes("?") ? "&" : "?"}${new URLSearchParams(params).toString()}`;
  const response = await fetch(callbackUrl);
  const text = await response.text();
  if (!response.ok || text.trim() !== "SUCCESS") {
    throw new Error(`callback failed: ${response.status} ${text}`);
  }
}

const server = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url, `http://127.0.0.1:${PORT}`);

  try {
    if (req.method === "GET" && requestUrl.pathname === "/health") {
      res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("ok");
      return;
    }

    if (req.method === "POST" && requestUrl.pathname === "/mapi.php") {
      const body = await parseBody(req);
      const params = Object.fromEntries(new URLSearchParams(body).entries());

      if (params.pid !== MERCHANT_PID) {
        sendJson(res, 400, { code: -1, msg: "invalid pid" });
        return;
      }

      const expectedSign = buildSign(params, MERCHANT_KEY);
      if (!params.sign || params.sign.toLowerCase() !== expectedSign.toLowerCase()) {
        sendJson(res, 400, { code: -1, msg: "invalid sign" });
        return;
      }

      const tradeNo = createTradeNo();
      const order = {
        tradeNo,
        outTradeNo: params.out_trade_no,
        money: params.money,
        type: params.type || "alipay",
        name: params.name || "Orion Key 本地支付",
        notifyUrl: params.notify_url,
        returnUrl: params.return_url,
        status: "PENDING",
      };
      ordersByTradeNo.set(tradeNo, order);
      tradeNoByOrderId.set(order.outTradeNo, tradeNo);

      const payUrl = `${PUBLIC_BASE_URL}/pay?trade_no=${encodeURIComponent(tradeNo)}`;
      sendJson(res, 200, {
        code: 1,
        msg: "success",
        trade_no: tradeNo,
        payurl: payUrl,
        qrcode: payUrl,
      });
      return;
    }

    if (req.method === "GET" && requestUrl.pathname === "/api.php") {
      const act = requestUrl.searchParams.get("act");
      const key = requestUrl.searchParams.get("key");
      const pid = requestUrl.searchParams.get("pid");
      const outTradeNo = requestUrl.searchParams.get("out_trade_no");

      if (act !== "order" || key !== MERCHANT_KEY || pid !== MERCHANT_PID || !outTradeNo) {
        sendJson(res, 400, { code: -1, msg: "invalid query" });
        return;
      }

      const tradeNo = tradeNoByOrderId.get(outTradeNo);
      const order = tradeNo ? ordersByTradeNo.get(tradeNo) : null;
      if (!order) {
        sendJson(res, 404, { code: -1, msg: "order not found" });
        return;
      }

      sendJson(res, 200, {
        code: 1,
        msg: "success",
        trade_no: order.tradeNo,
        out_trade_no: order.outTradeNo,
        money: order.money,
        status: order.status === "PAID" ? "1" : "0",
      });
      return;
    }

    if (req.method === "GET" && requestUrl.pathname === "/pay") {
      const tradeNo = requestUrl.searchParams.get("trade_no");
      const order = tradeNo ? ordersByTradeNo.get(tradeNo) : null;
      if (!order) {
        sendHtml(res, 404, "<h1>订单不存在</h1>");
        return;
      }
      sendHtml(res, 200, renderPayPage(order));
      return;
    }

    if (req.method === "POST" && requestUrl.pathname === "/complete") {
      const body = await parseBody(req);
      const tradeNo = new URLSearchParams(body).get("trade_no");
      const order = tradeNo ? ordersByTradeNo.get(tradeNo) : null;
      if (!order) {
        sendHtml(res, 404, "<h1>订单不存在</h1>");
        return;
      }

      if (order.status !== "PAID") {
        order.status = "PAID";
        try {
          await notifyOrderPaid(order);
          res.writeHead(302, { Location: order.returnUrl });
          res.end();
        } catch (error) {
          order.status = "PENDING";
          sendHtml(res, 502, renderPayPage(order, `回调本地项目失败：${error.message}`));
        }
      } else {
        res.writeHead(302, { Location: order.returnUrl });
        res.end();
      }
      return;
    }

    sendJson(res, 404, { code: -1, msg: "not found" });
  } catch (error) {
    sendJson(res, 500, { code: -1, msg: error.message || "internal error" });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Mock Epay listening on ${PORT}`);
});

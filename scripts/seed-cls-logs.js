const crypto = require("crypto");
const https = require("https");

const SECRET_ID = process.env.TENCENTCLOUD_SECRET_ID;
const SECRET_KEY = process.env.TENCENTCLOUD_SECRET_KEY;
const REGION = process.env.TENCENT_CLS_DEFAULT_REGION || "ap-guangzhou";
const HOST = process.env.TENCENTCLOUD_API_BASE_HOST
  ? `cls.${process.env.TENCENTCLOUD_API_BASE_HOST}`
  : "cls.tencentcloudapi.com";
const VERSION = "2020-10-16";

const TOPIC_ALIASES = {
  "application-logs": process.env.TENCENT_CLS_TOPIC_APPLICATION_LOGS,
  "system-events": process.env.TENCENT_CLS_TOPIC_SYSTEM_EVENTS,
  "database-slow-query": process.env.TENCENT_CLS_TOPIC_DATABASE_SLOW_QUERY,
};

function sha256Hex(data) {
  return crypto.createHash("sha256").update(data).digest("hex");
}

function hmac(key, data, encoding) {
  return crypto.createHmac("sha256", key).update(data).digest(encoding);
}

function tc3Headers(action, body, extraHeaders = {}) {
  const timestamp = Math.floor(Date.now() / 1000);
  const date = new Date(timestamp * 1000).toISOString().slice(0, 10);
  const service = "cls";

  const headers = {
    "content-type": extraHeaders["content-type"] || "application/json",
    host: HOST,
    "x-tc-action": action,
    "x-tc-region": REGION,
    "x-tc-timestamp": String(timestamp),
    "x-tc-version": VERSION,
    ...Object.fromEntries(
      Object.entries(extraHeaders)
        .filter(([key]) => key.toLowerCase() !== "content-type")
        .map(([key, value]) => [key.toLowerCase(), value]),
    ),
  };

  const sortedKeys = Object.keys(headers).sort();
  const canonicalHeaders = sortedKeys.map((key) => `${key}:${headers[key]}\n`).join("");
  const signedHeaders = sortedKeys.join(";");
  const canonicalRequest = [
    "POST",
    "/",
    "",
    canonicalHeaders,
    signedHeaders,
    sha256Hex(body),
  ].join("\n");
  const credentialScope = `${date}/${service}/tc3_request`;
  const stringToSign = [
    "TC3-HMAC-SHA256",
    timestamp,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join("\n");
  const secretDate = hmac(`TC3${SECRET_KEY}`, date);
  const secretService = hmac(secretDate, service);
  const secretSigning = hmac(secretService, "tc3_request");
  const signature = hmac(secretSigning, stringToSign, "hex");

  return {
    ...Object.fromEntries(Object.entries(headers).map(([key, value]) => [key.replace(/\b\w/g, (m) => m.toUpperCase()), value])),
    Authorization: `TC3-HMAC-SHA256 Credential=${SECRET_ID}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
  };
}

function request(action, body, extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const payload = Buffer.isBuffer(body) ? body : Buffer.from(JSON.stringify(body));
    const headers = {
      ...tc3Headers(action, payload, extraHeaders),
      "Content-Length": payload.length,
    };
    const req = https.request({ method: "POST", host: HOST, path: "/", headers }, (res) => {
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => {
        const text = Buffer.concat(chunks).toString("utf8");
        let parsed = text;
        try {
          parsed = JSON.parse(text);
        } catch {
          // UploadLog may return an empty body on success for some SDK paths.
        }
        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (parsed?.Response?.Error) {
            reject(new Error(`${action} failed: ${parsed.Response.Error.Code} ${parsed.Response.Error.Message}`));
            return;
          }
          resolve({ statusCode: res.statusCode, body: parsed });
        } else {
          reject(new Error(`${action} failed: HTTP ${res.statusCode} ${text}`));
        }
      });
    });
    req.on("error", reject);
    req.write(payload);
    req.end();
  });
}

function encodeURIComponentUpper(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, (char) =>
    `%${char.charCodeAt(0).toString(16).toUpperCase()}`,
  );
}

function v1Authorization(method, uri, query, headers) {
  const start = Math.floor(Date.now() / 1000) - 60;
  const end = start + 3600;
  const signTime = `${start};${end}`;
  const normalizedHeaders = Object.fromEntries(
    Object.entries(headers).map(([key, value]) => [key.toLowerCase(), String(value)]),
  );
  const headerKeys = Object.keys(normalizedHeaders).sort();
  const queryKeys = Object.keys(query).map((key) => key.toLowerCase()).sort();
  const formattedHeaders = headerKeys
    .map((key) => `${key}=${encodeURIComponentUpper(normalizedHeaders[key])}`)
    .join("&");
  const formattedParameters = queryKeys
    .map((key) => `${key}=${encodeURIComponentUpper(query[key])}`)
    .join("&");
  const httpRequestInfo = [
    method.toLowerCase(),
    uri,
    formattedParameters,
    formattedHeaders,
    "",
  ].join("\n");
  const stringToSign = [
    "sha1",
    signTime,
    crypto.createHash("sha1").update(httpRequestInfo).digest("hex"),
    "",
  ].join("\n");
  const signKey = crypto.createHmac("sha1", SECRET_KEY).update(signTime).digest("hex");
  const signature = crypto.createHmac("sha1", signKey).update(stringToSign).digest("hex");
  return [
    "q-sign-algorithm=sha1",
    `q-ak=${SECRET_ID}`,
    `q-sign-time=${signTime}`,
    `q-key-time=${signTime}`,
    `q-header-list=${headerKeys.join(";")}`,
    `q-url-param-list=${queryKeys.join(";")}`,
    `q-signature=${signature}`,
  ].join("&");
}

function uploadStructuredLog(topicId, body) {
  return new Promise((resolve, reject) => {
    const host = `${REGION}.cls.tencentcs.com`;
    const uri = "/structuredlog";
    const query = { topic_id: topicId };
    const path = `${uri}?topic_id=${encodeURIComponent(topicId)}`;
    const signedHeaders = {
      "content-type": "application/x-protobuf",
      host,
    };
    const headers = {
      Host: host,
      "Content-Type": signedHeaders["content-type"],
      Authorization: v1Authorization("POST", uri, query, signedHeaders),
      "Content-Length": body.length,
    };
    const req = https.request({ method: "POST", host, path, headers }, (res) => {
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => {
        const text = Buffer.concat(chunks).toString("utf8");
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve({ statusCode: res.statusCode, body: text || null });
        } else {
          reject(new Error(`UploadLog failed: HTTP ${res.statusCode} ${text}`));
        }
      });
    });
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

function varint(value) {
  const bytes = [];
  let n = BigInt(value);
  while (n >= 0x80n) {
    bytes.push(Number((n & 0x7fn) | 0x80n));
    n >>= 7n;
  }
  bytes.push(Number(n));
  return Buffer.from(bytes);
}

function fieldBytes(fieldNumber, wireType, payload) {
  return Buffer.concat([varint((fieldNumber << 3) | wireType), payload]);
}

function stringField(fieldNumber, value) {
  const payload = Buffer.from(String(value), "utf8");
  return fieldBytes(fieldNumber, 2, Buffer.concat([varint(payload.length), payload]));
}

function varintField(fieldNumber, value) {
  return fieldBytes(fieldNumber, 0, varint(value));
}

function messageField(fieldNumber, payload) {
  return fieldBytes(fieldNumber, 2, Buffer.concat([varint(payload.length), payload]));
}

function content(key, value) {
  return Buffer.concat([stringField(1, key), stringField(2, value)]);
}

function logItem(fields, timeSeconds) {
  const contents = Object.entries(fields).map(([key, value]) => messageField(2, content(key, value)));
  return Buffer.concat([varintField(1, timeSeconds), ...contents]);
}

function logGroup(logs, source) {
  const encodedLogs = logs.map((item) => messageField(1, logItem(item.fields, item.time)));
  return Buffer.concat([
    ...encodedLogs,
    stringField(3, "superbizagent-seed.log"),
    stringField(4, source),
  ]);
}

function logGroupList(logs, source) {
  return messageField(1, logGroup(logs, source));
}

async function discoverTopics() {
  if (Object.values(TOPIC_ALIASES).every(Boolean)) {
    return TOPIC_ALIASES;
  }
  const response = await request("DescribeTopics", { Limit: 50 });
  const topics = response.body.Response?.Topics || response.body.Topics || [];
  const resolved = { ...TOPIC_ALIASES };
  for (const topic of topics) {
    if (Object.prototype.hasOwnProperty.call(resolved, topic.TopicName) && !resolved[topic.TopicName]) {
      resolved[topic.TopicName] = topic.TopicId;
    }
  }
  return resolved;
}

function sampleLogs() {
  const now = Math.floor(Date.now() / 1000);
  return {
    "application-logs": [
      {
        time: now - 75,
        fields: {
          level: "ERROR",
          service: "order-service",
          instance: "pod-order-service-5c7d8e9f1-m3n2p",
          trace_id: "trace-order-oom-001",
          message: "Database connection pool exhausted, active 50/50, waiting 23",
          response_time: "4820",
          endpoint: "/api/orders",
        },
      },
      {
        time: now - 58,
        fields: {
          level: "WARN",
          service: "user-service",
          instance: "pod-user-service-6b4f8a2c1-qwerty",
          trace_id: "trace-user-slow-001",
          message: "Slow response detected, upstream order-service latency 4.7s",
          response_time: "5100",
          endpoint: "/api/users/profile",
        },
      },
      {
        time: now - 42,
        fields: {
          level: "ERROR",
          service: "payment-service",
          instance: "pod-payment-service-7d8f9c6b5-x2k4m",
          trace_id: "trace-payment-cpu-001",
          message: "CPU usage is high: 92.0%, process java, threads 245",
          response_time: "3320",
          endpoint: "/api/payments/callback",
        },
      },
    ],
    "system-events": [
      {
        time: now - 95,
        fields: {
          level: "WARN",
          event: "OOMKilled",
          service: "order-service",
          instance: "pod-order-service-5c7d8e9f1-m3n2p",
          message: "Pod restart: pod-order-service-5c7d8e9f1-m3n2p, reason OOMKilled, exit_code=137",
          namespace: "production",
        },
      },
      {
        time: now - 35,
        fields: {
          level: "INFO",
          event: "DeploymentChanged",
          service: "payment-service",
          instance: "deployment/payment-service",
          message: "Deployment rollout completed for payment-service version v2026.05.19-rc1",
          namespace: "production",
        },
      },
    ],
    "database-slow-query": [
      {
        time: now - 88,
        fields: {
          level: "WARN",
          service: "order-service",
          db: "order_db",
          query_time: "4.823",
          lock_time: "0.000123",
          rows_examined: "12480",
          full_table_scan: "true",
          message: "Slow SQL SELECT_ORDER_BY_USER: SELECT * FROM orders WHERE user_id='usr_9a8b7c6d' AND status IN ('pending','processing') ORDER BY created_at DESC LIMIT 100",
        },
      },
      {
        time: now - 67,
        fields: {
          level: "WARN",
          service: "user-service",
          db: "user_db",
          query_time: "3.214",
          lock_time: "0.000092",
          rows_examined: "8800",
          full_table_scan: "true",
          message: "Slow SQL USER_PROFILE_JOIN: SELECT u.*, p.* FROM users u JOIN profiles p ON u.id=p.user_id WHERE u.status='active'",
        },
      },
    ],
  };
}

function textValue(sqlFlag = true) {
  return {
    Type: "text",
    Tokenizer: "@&?|#()='\",;:<>[]{} \n\t\r",
    SqlFlag: sqlFlag,
    ContainZH: true,
  };
}

function longValue() {
  return {
    Type: "long",
    SqlFlag: true,
  };
}

function doubleValue() {
  return {
    Type: "double",
    SqlFlag: true,
  };
}

async function ensureIndex(topicName, topicId) {
  const commonFields = [
    ["level", textValue()],
    ["service", textValue()],
    ["instance", textValue()],
    ["message", textValue()],
    ["trace_id", textValue()],
  ];
  const topicFields = {
    "application-logs": [
      ["response_time", longValue()],
      ["endpoint", textValue()],
    ],
    "system-events": [
      ["event", textValue()],
      ["namespace", textValue()],
    ],
    "database-slow-query": [
      ["db", textValue()],
      ["query_time", doubleValue()],
      ["lock_time", doubleValue()],
      ["rows_examined", longValue()],
      ["full_table_scan", textValue()],
    ],
  };
  const keyValues = [...commonFields, ...(topicFields[topicName] || [])].map(([Key, Value]) => ({ Key, Value }));
  const payload = {
    TopicId: topicId,
    Status: true,
    IncludeInternalFields: true,
    Rule: {
      FullText: {
        CaseSensitive: false,
        Tokenizer: "@&?|#()='\",;:<>[]{} \n\t\r",
        ContainZH: true,
      },
      KeyValue: {
        CaseSensitive: false,
        KeyValues: keyValues,
      },
    },
  };

  try {
    return await request("CreateIndex", payload);
  } catch (error) {
    if (String(error.message).includes("IndexConflict") || String(error.message).includes("already")) {
      return { skipped: true, message: error.message };
    }
    throw error;
  }
}

async function main() {
  if (!SECRET_ID || !SECRET_KEY) {
    throw new Error("Set TENCENTCLOUD_SECRET_ID and TENCENTCLOUD_SECRET_KEY before running this script.");
  }

  const topics = await discoverTopics();
  const logsByTopic = sampleLogs();
  const results = [];

  for (const [name, logs] of Object.entries(logsByTopic)) {
    const topicId = topics[name];
    if (!topicId) {
      throw new Error(`Missing topic id for ${name}. Create the CLS topic or set the matching TENCENT_CLS_TOPIC_* env var.`);
    }
    const index = process.env.CLS_SEED_ENSURE_INDEX === "true"
      ? await ensureIndex(name, topicId)
      : { skipped: true, reason: "Set CLS_SEED_ENSURE_INDEX=true to create indexes from this script." };
    const body = logGroupList(logs, `seed-${name}`);
    const response = await uploadStructuredLog(topicId, body);
    results.push({ topicName: name, topicId, count: logs.length, index, response });
  }

  console.log(JSON.stringify({ region: REGION, results }, null, 2));
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});

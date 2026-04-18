import { NextRequest, NextResponse } from "next/server"

export const runtime = "nodejs"
export const dynamic = "force-dynamic"

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8083"

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
])

const PROXY_ONLY_BROWSER_HEADERS = new Set([
  "origin",
  "referer",
  "sec-fetch-dest",
  "sec-fetch-mode",
  "sec-fetch-site",
  "sec-fetch-user",
])

function buildTargetUrl(request: NextRequest, path: string[]) {
  const backendBase = BACKEND_URL.replace(/\/$/, "")
  const apiPath = path.map(encodeURIComponent).join("/")
  const search = request.nextUrl.search
  return `${backendBase}/api/${apiPath}${search}`
}

function copyRequestHeaders(request: NextRequest) {
  const headers = new Headers(request.headers)
  for (const header of HOP_BY_HOP_HEADERS) {
    headers.delete(header)
  }
  // Requests already arrive at the same-origin Next.js server, so forwarding the
  // browser's CORS context to Spring Boot only causes the backend to treat them
  // as cross-origin and reject production domains that are not in its allowlist.
  for (const header of PROXY_ONLY_BROWSER_HEADERS) {
    headers.delete(header)
  }
  return headers
}

function copyResponseHeaders(response: Response) {
  const headers = new Headers(response.headers)
  for (const header of HOP_BY_HOP_HEADERS) {
    headers.delete(header)
  }
  return headers
}

async function proxy(request: NextRequest, path: string[]) {
  const method = request.method.toUpperCase()
  const targetUrl = buildTargetUrl(request, path)
  const headers = copyRequestHeaders(request)

  const init: RequestInit = {
    method,
    headers,
    redirect: "manual",
    cache: "no-store",
  }

  if (method !== "GET" && method !== "HEAD") {
    const body = await request.arrayBuffer()
    if (body.byteLength > 0) {
      init.body = body
    }
  }

  try {
    const response = await fetch(targetUrl, init)
    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: copyResponseHeaders(response),
    })
  } catch (error) {
    console.error(`API proxy failed: ${targetUrl}`, error)
    return NextResponse.json(
      {
        code: 502,
        message: "Backend service unavailable",
      },
      { status: 502 }
    )
  }
}

type RouteContext = {
  params: Promise<{
    path: string[]
  }>
}

export async function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

export async function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

export async function OPTIONS(request: NextRequest, context: RouteContext) {
  return proxy(request, (await context.params).path)
}

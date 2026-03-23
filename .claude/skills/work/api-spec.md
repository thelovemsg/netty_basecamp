# API Specification

## Base URL
```
https://localhost:8080
```

---

## 라우팅 원리

### 요청 처리 흐름

```
클라이언트 요청
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  Netty Channel Pipeline                                 │
│                                                         │
│  1. HttpServerCodec        HTTP 바이트 ↔ 객체 변환       │
│  2. HttpObjectAggregator   청크된 메시지를 FullHttpRequest │
│                            하나로 합침 (최대 64KB)        │
│  3. HttpRoutingHandler     라우트 매칭 + 핸들러 실행       │
└─────────────────────────────────────────────────────────┘
```

### 라우트 등록 구조

```
서버 시작 시 (AppConfig.initRoutes())
    │
    ▼
MemberRouteConfig.routes()  →  List<RouteEntry> 반환
    │                              │
    │   (향후 추가)                  │
    │   FareRouteConfig.routes()   │
    │   CouponRouteConfig.routes() │
    │                              ▼
    └──────────────────────►  RouteRegistry
                                ├── exactRoutes   (HashMap)  → "GET /api/members" 같은 정확 매칭
                                └── paramRoutes   (List)     → "/api/members/{id}" 같은 패턴 매칭
```

### 라우트 매칭 순서 (RouteRegistry.find)

1. **정확 매칭 (exactRoutes)** — `method + " " + path`를 키로 HashMap 조회. O(1)
2. **패턴 매칭 (paramRoutes)** — 정확 매칭 실패 시, 등록된 패턴 라우트를 순회하며 세그먼트 비교
   - 세그먼트 수가 같아야 함 (`/api/members/42` → 3개 세그먼트)
   - `{varName}` 부분은 와일드카드로 취급, 실제 값을 pathVariables에 추출
   - 첫 번째 매칭된 라우트 반환
3. **매칭 실패** → `null` 반환 → 404 응답

### 요청 → 핸들러 실행 과정 (HttpRoutingHandler.channelRead0)

```
FullHttpRequest 도착
    │
    ├── URI에서 path 추출          "/api/members/42?page=1" → "/api/members/42"
    ├── RouteRegistry.find()       RouteMatch (RouteEntry + pathVariables) 반환
    │       └── 실패 시 404 응답
    │
    ├── RequestContext 조립
    │       ├── method          "GET"
    │       ├── path            "/api/members/42"
    │       ├── pathVariables   {id: "42"}
    │       ├── queryParams     {page: "1"}
    │       ├── headers         {Content-Type: "application/json", ...}
    │       └── body            "{...}" (JSON 문자열)
    │
    ├── RouteFilter 체인 실행     (현재 미적용, AuthFilter 등 확장 가능)
    │
    ├── RouteEntry.handle(ctx)    핸들러 람다 실행 → 도메인 서비스 호출
    │
    └── 결과를 JSON으로 직렬화하여 응답
```

### 예외 → HTTP 상태 코드 매핑

| 예외 | 상태 코드 |
|------|-----------|
| `UnauthorizedException` | 401 Unauthorized |
| `IllegalArgumentException` | 400 Bad Request |
| 그 외 `Exception` | 500 Internal Server Error |

---

## Member API

### 공통 응답 형식
- Content-Type: `application/json`
- 모든 응답은 JSON 객체

### Member 객체

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 자동 생성 ID |
| name | String | 이름 |
| address | String | 주소 |
| age | int | 나이 |
| createdAt | Long | 생성 시각 (epoch millis) |
| modifiedAt | Long | 수정 시각 (epoch millis) |

---

### POST /api/members

회원 생성

**Request Body**
```json
{
  "name": "홍길동",
  "address": "서울",
  "age": 30
}
```

**Response** `200 OK`
```json
{
  "id": 1,
  "name": "홍길동",
  "address": "서울",
  "age": 30,
  "createdAt": 1742025600000,
  "modifiedAt": 1742025600000
}
```

**Error** `400 Bad Request` — 잘못된 JSON
```json
{
  "error": "Invalid JSON: ..."
}
```

---

### GET /api/members

전체 회원 조회

**Response** `200 OK`
```json
[
  {
    "id": 1,
    "name": "홍길동",
    "address": "서울",
    "age": 30,
    "createdAt": 1742025600000,
    "modifiedAt": 1742025600000
  }
]
```

---

### GET /api/members/{id}

단건 회원 조회

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| id | Long | 회원 ID |

**Response** `200 OK`
```json
{
  "id": 1,
  "name": "홍길동",
  "address": "서울",
  "age": 30,
  "createdAt": 1742025600000,
  "modifiedAt": 1742025600000
}
```

**Error** `400 Bad Request` — 존재하지 않는 ID
```json
{
  "error": "Member not found: 999"
}
```

---

### PUT /api/members/{id}

회원 정보 수정

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| id | Long | 회원 ID |

**Request Body**
```json
{
  "name": "수정됨",
  "address": "부산",
  "age": 31
}
```

**Response** `200 OK`
```json
{
  "id": 1,
  "name": "수정됨",
  "address": "부산",
  "age": 31,
  "createdAt": 1742025600000,
  "modifiedAt": 1742029200000
}
```

---

### DELETE /api/members/{id}

회원 삭제

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| id | Long | 회원 ID |

**Response** `200 OK`
```json
{
  "message": "Deleted member: 1"
}
```

---

## curl 예시

```bash
# 회원 생성
curl -k -X POST https://localhost:8080/api/members \
  -H "Content-Type: application/json" \
  -d '{"name":"홍길동","address":"서울","age":30}'

# 전체 조회
curl -k https://localhost:8080/api/members

# 단건 조회
curl -k https://localhost:8080/api/members/1

# 수정
curl -k -X PUT https://localhost:8080/api/members/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"수정됨","address":"부산","age":31}'

# 삭제
curl -k -X DELETE https://localhost:8080/api/members/1
```

> `-k` 옵션: 자체 서명 인증서(Bouncy Castle) 사용으로 SSL 검증 스킵

# Commit Message Template

커밋 시 Claude에게 이 문서를 참고하여 커밋 메시지 후보를 3개 생성하도록 요청한다.

---

## 형식

```
<type>(<scope>): <subject>

<body>
```

## Type

| type | 용도 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 기능 변경 없는 코드 구조 개선 |
| test | 테스트 추가/수정 |
| docs | 문서 변경 |
| chore | 빌드, 설정 등 기타 변경 |

## Scope

변경 대상 영역. 예시:
- `route`, `filter`, `member`, `coupon`, `fare`
- `config`, `handler`, `repository`

## Subject 규칙

- 한글로 작성
- 50자 이내
- "~했다" 형태의 과거형 사용
- 무엇을 했는지가 아니라 **왜 했는지** 중심

## Body 규칙

- 변경사항을 번호로 나열
- 각 항목은 구체적으로

---

## 예시

```
refactor(route): 핸들러 확장성을 위해 RequestContext를 도입했다

1. BiFunction 핸들러를 Function<RequestContext, Object>로 변경
2. RouteMatch 도입으로 RouteRegistry.find() 반환값 정리
3. MemberRouteConfig 핸들러를 ctx 기반으로 전환
```

```
feat(filter): 인증 필터 구조를 추가했다

1. RouteFilter 인터페이스 정의
2. AuthFilter 스켈레톤 구현 (Authorization 헤더 검사)
3. HttpRoutingHandler에 필터 체인 연결
```

```
fix(member): 삭제된 회원 재조회 시 NPE를 방지했다

1. MemberRepository.findById()에서 null 체크 추가
2. 예외 메시지에 요청된 ID 포함
```

---

## 사용법

커밋할 때 Claude에게:
> "docs/commit_template.md 읽고 커밋 메시지 3개 만들어줘"

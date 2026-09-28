# 폭우 트래픽 대비 구조와 검증 방법

## 적용된 구조

```text
Client
  ├─ GET /api/risk ─────────── Redis (20초)
  │                              ├─ miss: Seoul rainfall API (Redis 120초)
  │                              └─ miss: MySQL Replica → 실패 시 Primary
  ├─ GET /api/reports/page ─── MySQL Replica → 실패 시 Primary
  └─ POST /api/reports ─────── S3 + MySQL Primary
```

- `/api/reports`는 기존 프론트 호환을 위해 배열 응답을 유지하되 최신 100건만 반환합니다. `limit`은 최대 500입니다.
- `/api/reports/page?size=50&beforeId=...`는 `id` 커서를 사용합니다. 큰 OFFSET 조회를 피합니다.
- `/api/weather/rainfall`는 서울시 원본 응답을 Redis에 120초 보관합니다. Redis 장애 때 서울시 API를 직접 호출하고, 서울시 API까지 실패하면 최근 15분 이내 메모리 값을 반환합니다.
- `/api/risk?district=강남구`는 20초 캐시를 사용합니다. 점수는 공식 재난 기준이 아니라 최근 강우량과 서울시 전체 신고 수를 조합한 서비스 내부 지표입니다.
- `RISK_CACHE_ENABLED=false`로 위험도 응답 캐시만 우회할 수 있습니다. 강우 공공데이터 캐시는 유지되므로 같은 시나리오에서 위험도 캐시의 효과를 안전하게 비교할 수 있습니다.
- Replica 조회가 실패하면 같은 쿼리를 Primary에서 한 번 재시도합니다. 쓰기는 항상 Primary에서 수행합니다.

## 현재 무료 운영 환경의 한계

운영 캐시는 Render의 무료 Key Value(Valkey, Redis 호환)를 사용합니다. 백엔드와 같은 Oregon 리전의 내부 네트워크로 연결하며, `allkeys-lru`와 비영속 모드로 실행합니다. 캐시가 재시작되어 데이터가 사라져도 원본에서 다시 채워집니다. 애플리케이션 시작 로그의 `Redis cache connection verified (PING=PONG)`으로 실제 연결을 확인합니다.

현재 Aiven 무료 MySQL은 단일 노드이므로 운영 환경에서 실제 DB 이중화가 활성화된 상태는 아닙니다. 코드는 `DB_REPLICA_URL`이 있을 때 읽기를 분리하도록 준비되어 있고, 아래 Docker 구성에서 장애 전환 동작을 재현할 수 있습니다. 운영에서 Primary–Replica를 적용했다고 표현하려면 실제 Replica가 제공되는 DB 요금제나 별도 DB 인스턴스를 연결한 뒤 장애 테스트 결과를 남겨야 합니다.

## 로컬 실행

Docker Desktop 설치 후:

```bash
docker compose -f docker-compose.ha.yml up -d
docker compose -f docker-compose.ha.yml ps
```

백엔드 실행 환경변수 예시는 `.env.ha.example`에 있습니다. 애플리케이션에는 기존 필수값인 `SEOUL_API_KEY`, `AUTH_TOKEN_SECRET`, AWS 값도 함께 설정해야 합니다.

Replica 상태 확인:

```bash
docker exec flood-mysql-replica mysql -uroot -plocal-root-password -e "SHOW REPLICA STATUS"
```

출력의 `Replica_IO_Running`과 `Replica_SQL_Running`이 모두 `Yes`인지 확인합니다.

## k6 부하 테스트

운영 무료 서버는 다른 사용자가 없는 시간에 낮은 단계부터 실행합니다. 먼저 로컬에서 기준값을 만듭니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 load-tests/storm-read.js
```

배포 서버 확인은 다음처럼 실행합니다.

```bash
k6 run -e BASE_URL=https://startup-backend-t4qw.onrender.com load-tests/storm-read.js
```

Render 무료 인스턴스는 휴면 해제 시간이 측정에 섞이므로 테스트 전에 `/api/reports?limit=1`을 한 번 호출하고 정상 응답을 확인합니다.

신고 생성은 S3 객체, DB 행, 사용자 포인트를 실제로 변경하므로 조회 테스트와 분리했습니다. 테스트 전용 계정으로 로그인해 받은 토큰을 사용하고, 대상 주소를 두 번 입력해야 실행됩니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e AUTH_TOKEN=테스트계정토큰 -e CONFIRM_WRITES=http://localhost:8080 load-tests/storm-report-write.js
```

쓰기 시나리오는 2→5→10 RPS로 올라갑니다. 테스트 뒤 `description`이 `k6 storm write test`로 시작하는 신고와 S3 이미지를 삭제합니다.

기록할 값:

| 단계 | 설정 | 요청 실패율 | p95 | 처리량 | 비고 |
|---|---|---:|---:|---:|---|
| 위험도 캐시 OFF | 위험도 응답 캐시만 우회, 강우 캐시 유지 | 0.00% | 전체 4,143.43ms / 위험도 4,187.66ms | 평균 18.19 RPS | 3,548건 완료, dropped 126 |
| 위험도 캐시 ON | 위험도와 강우 Redis 캐시 사용 | 0.00% | 전체 663.79ms / 위험도 393.52ms | 평균 18.82 RPS | 3,670건 완료, dropped 4 |
| 읽기 분리 | Redis + Replica |  |  |  |  |
| Redis 장애 | 테스트 중 Redis 중지 |  |  |  | 원본 조회 전환 확인 |
| Replica 장애 | 테스트 중 Replica 중지 |  |  |  | Primary 재시도 확인 |

### 위험도 캐시 A/B 측정 결과 (2026-09-28)

- 대상: Render 무료 Web Service + Render 무료 Key Value + Aiven 무료 MySQL
- 시나리오: 읽기 요청을 3분 15초 동안 5→15→30→50 RPS로 증가
- 비교 조건: 위험도 응답 캐시만 OFF/ON으로 변경하고 강우 공공데이터 캐시는 양쪽 모두 유지
- 캐시 OFF: 3,548건 완료, HTTP 실패 0건, 전체 p95 4,143.43ms, 위험도 p95 4,187.66ms, dropped 126건
- 캐시 ON: 3,670건 완료, HTTP 실패 0건, 전체 p95 663.79ms, 위험도 p95 393.52ms, dropped 4건
- 개선 결과: 전체 p95 84.0% 감소, 위험도 p95 90.6% 감소, dropped iterations 96.8% 감소
- 판정: 캐시 OFF는 전체 p95 1초 미만과 위험도 p95 800ms 미만 기준을 통과하지 못했고, 캐시 ON은 모든 임계값을 통과

50 RPS는 램프 구간의 최대 목표값이며 지속 처리량을 뜻하지 않습니다. 이번 비교는 공유 무료 인프라에서 조건별로 한 번씩 측정했으므로 최대 수용량이나 장기 안정성을 보장하지 않습니다. DB 쿼리 수는 계측하지 않아 감소량을 수치로 표현하지 않습니다. 원본 결과는 `load-tests/results/`에 보관합니다.

## 장애 주입

부하 테스트 중 다음 명령을 각각 실행하고 5xx 비율과 로그를 확인합니다.

```bash
docker stop flood-redis
docker start flood-redis

docker stop flood-mysql-replica
docker start flood-mysql-replica
```

Replica를 다시 시작한 뒤 복제가 재개되지 않으면 다음 명령으로 초기화 작업을 다시 실행합니다.

```bash
docker compose -f docker-compose.ha.yml run --rm replica-init
```

## 자소서 문장 사용 기준

현재 검증한 범위는 다음처럼 표현할 수 있습니다.

> 반복 조회되는 지역 위험도에 Redis 캐시를 적용하고 동일한 5→50 RPS k6 시나리오로 적용 전후를 비교했습니다. 캐시 적용 후 위험도 조회 p95가 4,188ms에서 394ms로 90.6% 감소했고, 시작하지 못한 반복 요청이 126건에서 4건으로 줄었습니다. 두 조건 모두 HTTP 요청 실패는 없었습니다.

현재 Aiven 무료 MySQL은 단일 노드입니다. 실제 운영 Replica를 연결하고 장애 전환을 검증하기 전에는 운영 환경에 Primary–Replica 이중화를 적용했다고 표현하면 안 됩니다. Primary 장애 시 자동 승격 기능도 현재 범위에 포함되지 않습니다.

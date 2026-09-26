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
| 기준 | Redis 끔, Replica 끔 |  |  |  |  |
| 캐시 | Redis 켬 |  |  |  |  |
| 읽기 분리 | Redis + Replica |  |  |  |  |
| Redis 장애 | 테스트 중 Redis 중지 |  |  |  | 원본 조회 전환 확인 |
| Replica 장애 | 테스트 중 Replica 중지 |  |  |  | Primary 재시도 확인 |

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

측정 전에는 다음처럼 구현 사실만 표현합니다.

> 반복 조회되는 강우 데이터와 지역 위험도에 Redis 캐시를 적용하고, Redis 장애 시 원본 조회로 전환하도록 구성했습니다. 읽기 요청은 MySQL Replica로 분리하고 Replica 장애 시 Primary로 재시도하도록 구현했으며, k6 시나리오로 집중호우 상황의 조회 트래픽과 장애 상황을 검증했습니다.

실제 운영 Replica 연결과 부하 테스트를 완료한 뒤에는 표의 수치로 캐시 적중 전후 DB 쿼리 수, p95 응답 시간, 최대 안정 처리량을 함께 제시합니다. Primary 장애 시 자동 승격 기능은 현재 범위에 포함되지 않으므로 해당 기능을 구현했다고 표현하면 안 됩니다.

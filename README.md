# demoredis-lettuce

A minimal reactive Spring Boot application that demonstrates a fast and efficient cache layer using:
- Spring WebFlux for non-blocking REST APIs
- Spring Data Redis (Lettuce driver) for reactive cache access
- Kryo for compact, high‑performance binary serialization of cached values
- Postgres via R2DBC for the primary data store

The sample exposes a small `Item` API, caches `Item` reads in Redis with a short TTL, and shows cache hit/miss behavior with counters returned to the client.


## Overview
- Reactive stack end to end: WebFlux HTTP layer → reactive repository (R2DBC) → reactive Redis cache (Lettuce).
- Cache details:
  - Cache name: `itemCache` (see `RedisConfig.ITEM_CACHE`).
  - TTL: 15 seconds.
  - Keys: strings; Values: custom Redis serializer backed by Kryo for speed and compactness.
  - `ItemService#getItemById` demonstrates programmatic cache access to include hit/miss counters in the response; `getSimpleItemById` shows the simpler `@Cacheable` approach.
  - Updates (`PUT /items/{id}`) evict the cache entry so the next read rehydrates from the DB.
- Actuator metrics and health endpoints are enabled; Prometheus scraping endpoint is exposed.


## How WebFlux + Lettuce + Kryo fit together
- WebFlux: Uses the Reactor programming model (`Mono`/`Flux`) to handle requests without blocking threads, scaling efficiently under load.
- Lettuce: Spring Data Redis Reactive uses the Lettuce client under the hood, which is fully non-blocking and integrates with Reactor. This keeps Redis operations from blocking the event loop.
- Kryo: A custom `RedisSerializer` (`KryoRedisSerializer`) writes/reads objects as fast binary payloads. Compared with JSON/JDK serialization, Kryo reduces payload size and GC pressure and speeds up cache access. A `ThreadLocal` Kryo instance is used because Kryo is not thread-safe.


## Tech stack
- Language: Java 25
- Build: Maven (Spring Boot parent 3.5.7)
- Frameworks:
  - Spring Boot 3.5.x
  - Spring WebFlux
  - Spring Data Redis Reactive (Lettuce)
  - Spring Data R2DBC (Postgres)
  - Spring Cache
  - Spring Boot Actuator
- Testing:
  - JUnit 5, Reactor Test
  - Testcontainers (Postgres, Redis)
- Serialization: Kryo 5.x

Entry point: `com.github.darekdan.demoredislettuce.DemoredisLettuceApplication`


## Requirements
- Java 25 (ensure `JAVA_HOME` points to JDK 25)
- Maven 3.9+
- Docker (optional but recommended for running Redis/Postgres locally)


## Quick start (local)
1) Start infrastructure (choose one):
- With Docker (example images/tags; adjust as needed):
  - Redis:
    ```bash
    docker run --name redis -p 6379:6379 -d redis:7
    ```
  - Postgres (with database `demo`):
    ```bash
    docker run --name pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=demo -p 5432:5432 -d postgres:16
    ```
- Or rely on Testcontainers for tests only. Note: the application itself does not auto-start containers; you must provide Redis and Postgres when running the app.

2) Configure environment variables (examples):
- Redis
  - `SPRING_DATA_REDIS_HOST=localhost`
  - `SPRING_DATA_REDIS_PORT=6379`
- Postgres (R2DBC)
  - `SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/demo`
  - `SPRING_R2DBC_USERNAME=postgres`
  - `SPRING_R2DBC_PASSWORD=postgres`

3) Run the app:
```bash
mvn spring-boot:run
```
The app starts on `http://localhost:8080` by default.

Alternatively, build a jar and run it:
```bash
mvn -DskipTests package
java -jar target/demoredis-lettuce-0.0.1-SNAPSHOT.jar
```


## API
Base path: `http://localhost:8080`

- GET `/items/{id}` → returns an `ItemResponse` with one of: “Retrieved from cache” or “Retrieved from database” and hit/miss counters.
- POST `/items` → create an item. Body example:
  ```json
  { "name": "Book", "description": "A great book" }
  ```
- PUT `/items/{id}` → update an item. Evicts the cache entry for `{id}`.
- POST `/items/reset-counters` → reset internal counters used in responses.

Actuator (selected ones exposed):
- GET `/actuator/health`
- GET `/actuator/info`
- GET `/actuator/metrics`
- GET `/actuator/prometheus`


## Example usage
- Create:
  ```bash
  curl -X POST http://localhost:8080/items \
       -H "Content-Type: application/json" \
       -d '{"name":"Book","description":"A great book"}'
  ```
- Read twice to observe cache:
  ```bash
  curl http://localhost:8080/items/1
  curl http://localhost:8080/items/1
  ```
- Update (evicts cache):
  ```bash
  curl -X PUT http://localhost:8080/items/1 \
       -H "Content-Type: application/json" \
       -d '{"name":"Book 2","description":"Updated"}'
  ```


## Configuration & environment variables
See `src/main/resources/application.properties` for defaults. You can override via env vars or `--property=value` flags. Useful keys:
- `spring.data.redis.host` / `SPRING_DATA_REDIS_HOST`
- `spring.data.redis.port` / `SPRING_DATA_REDIS_PORT`
- `spring.r2dbc.url` / `SPRING_R2DBC_URL`
- `spring.r2dbc.username` / `SPRING_R2DBC_USERNAME`
- `spring.r2dbc.password` / `SPRING_R2DBC_PASSWORD`
- Actuator exposure in `management.endpoints.web.exposure.include`

Schema initialization: `schema.sql` is present and R2DBC initialization is enabled (`spring.r2dbc.initialization-mode=always`).


## Running tests
Tests use Testcontainers to start isolated Postgres and Redis instances.
```bash
mvn test
```
Relevant classes:
- `src/test/java/.../TestcontainersConfiguration.java`
- `src/test/java/.../DemoredisLettuceApplicationTests.java`

Images referenced (check tags locally): `postgres:latest`, `redis:latest`.


## Load testing (k6)
A simple k6 script is included: `k6/loadTest.js`.
Run with:
```bash
k6 run k6/loadTest.js
```
- Tip: ensure the base URL inside the script matches your running app. If the script expects an env var like `BASE_URL`, set it accordingly.

```bash
         /\      Grafana   /‾‾/                                                                                                                                                                                                                                                                                                                                                                                                                                                    
    /\  /  \     |\  __   /  /                                                                                                                                                                                                                                                                                                                                                                                                                                                     
   /  \/    \    | |/ /  /   ‾‾\                                                                                                                                                                                                                                                                                                                                                                                                                                                   
  /          \   |   (  |  (‾)  |                                                                                                                                                                                                                                                                                                                                                                                                                                                  
 / __________ \  |_|\_\  \_____/ 

     execution: local
        script: .\k6\loadTest.js
        output: -

     scenarios: (100.00%) 2 scenarios, 700 max VUs, 1m30s max duration (incl. graceful stop):
              * read_scenario: 900.00 iterations/s for 1m0s (maxVUs: 100-500, exec: readTest, gracefulStop: 30s)
              * write_scenario: 100.00 iterations/s for 1m0s (maxVUs: 50-200, exec: writeTest, gracefulStop: 30s)



  █ THRESHOLDS 

    cache_hit_rate{scenario:read_scenario}
    ✓ 'rate>0.5' rate=86.35%

    http_req_duration
    ✓ 'p(95)<200' p(95)=3.73ms

    read_failure_rate{scenario:read_scenario}
    ✓ 'rate<0.01' rate=0.00%

    read_request_duration{scenario:read_scenario}
    ✓ 'p(95)<50' p(95)=3.7074

    write_failure_rate{scenario:write_scenario}
    ✓ 'rate<0.01' rate=0.00%

    write_request_duration{scenario:write_scenario}
    ✓ 'p(95)<150' p(95)=4.2581


  █ TOTAL RESULTS 

    checks_total.......................: 65899   1097.3409/s
    checks_succeeded...................: 100.00% 65899 out of 65899
    checks_failed......................: 0.00%   0 out of 65899

    ✓ PUT status is 200
    ✓ PUT response has new name
    ✓ GET status is 200

    CUSTOM
    cache_hit_rate.................................................................: 86.35% 46542 out of 53897
      { scenario:read_scenario }...................................................: 86.35% 46542 out of 53897
    read_failure_rate..............................................................: 0.00%  0 out of 53897
      { scenario:read_scenario }...................................................: 0.00%  0 out of 53897
    read_request_duration..........................................................: avg=2.932148 min=0      med=1.0779  max=329.1009 p(90)=3.1463  p(95)=3.7074 
      { scenario:read_scenario }...................................................: avg=2.932148 min=0      med=1.0779  max=329.1009 p(90)=3.1463  p(95)=3.7074 
    write_failure_rate.............................................................: 0.00%  0 out of 6001
      { scenario:write_scenario }..................................................: 0.00%  0 out of 6001
    write_request_duration.........................................................: avg=4.642065 min=1.5364 med=2.6446  max=305.3101 p(90)=3.6908  p(95)=4.2581 
      { scenario:write_scenario }..................................................: avg=4.642065 min=1.5364 med=2.6446  max=305.3101 p(90)=3.6908  p(95)=4.2581 

    HTTP
    http_req_duration..............................................................: avg=3.1ms    min=0s     med=1.13ms  max=329.1ms  p(90)=3.18ms  p(95)=3.73ms 
      { expected_response:true }...................................................: avg=3.1ms    min=0s     med=1.13ms  max=329.1ms  p(90)=3.18ms  p(95)=3.73ms 
    http_req_failed................................................................: 0.00%  0 out of 59898
    http_reqs......................................................................: 59898  997.41309/s

    EXECUTION
    dropped_iterations.............................................................: 104    1.731793/s
    iteration_duration.............................................................: avg=17.48ms  min=10ms   med=11.69ms max=356.66ms p(90)=52.26ms p(95)=53.14ms
    iterations.....................................................................: 59898  997.41309/s
    vus............................................................................: 16     min=15             max=18 
    vus_max........................................................................: 246    min=246            max=246

    NETWORK
    data_received..................................................................: 14 MB  240 kB/s
    data_sent......................................................................: 5.5 MB 92 kB/s




running (1m00.1s), 000/246 VUs, 59898 complete and 0 interrupted iterations
read_scenario  ✓ [======================================] 000/196 VUs  1m0s  900.00 iters/s
write_scenario ✓ [======================================] 000/050 VUs  1m0s  100.00 iters/s
```

## Project structure
```
README.md
k6/loadTest.js
pom.xml
src/main/java/com/github/darekdan/demoredislettuce/
  DemoredisLettuceApplication.java
  Item.java
  ItemController.java
  ItemRepository.java
  ItemResponse.java
  ItemService.java
  KryoRedisSerializer.java
  RedisConfig.java
src/main/resources/
  application.properties
  logback-spring.xml
  schema.sql
src/test/java/com/github/darekdan/demoredislettuce/
  DemoredisLettuceApplicationTests.java
  TestDemoredisLettuceApplication.java
  TestcontainersConfiguration.java
```


## Scripts and useful Maven goals
- Run app: `mvn spring-boot:run`
- Run tests: `mvn test`
- Package: `mvn package`
- Build container image: `mvn spring-boot:build-image`


## Notes on caching configuration
- Defined in `RedisConfig` with `RedisCacheManagerBuilderCustomizer`.
- Default cache uses string serializers for keys/values; `itemCache` overrides the value serializer to the custom Kryo serializer and sets TTL to 15 seconds.
- `Item` implements `Serializable` and classes are registered in `KryoRedisSerializer` for optimal performance.


## License
TODO: Add a license (e.g., MIT, Apache-2.0) and update the POM `licenses` section accordingly.


## References
- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.7/maven-plugin)
- [Create an OCI image](https://docs.spring.io/spring-boot/3.5.7/maven-plugin/build-image.html)
- [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/3.5.7/reference/testing/testcontainers.html#testing.testcontainers)
- [Testcontainers Postgres Module Reference Guide](https://java.testcontainers.org/modules/databases/postgres/)
- [Spring Data Reactive Redis](https://docs.spring.io/spring-boot/3.5.7/reference/data/nosql.html#data.nosql.redis)
- [Testcontainers](https://java.testcontainers.org/)
- [Spring Reactive Web](https://docs.spring.io/spring-boot/3.5.7/reference/web/reactive.html)


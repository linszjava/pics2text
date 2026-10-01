.PHONY: verify package web-install web-test web-build web-dev infra-up infra-down infra-status api analysis-worker render-worker render-worker-docker

verify:
	./mvnw verify

package:
	./mvnw clean package

web-install:
	cd mosaic-web && npm ci

web-test:
	cd mosaic-web && npm test

web-build:
	cd mosaic-web && npm run build

web-dev:
	cd mosaic-web && npm run dev

infra-up:
	docker compose up -d

infra-down:
	docker compose down

infra-status:
	docker compose ps

api:
	./mvnw -pl mosaic-api -am package -DskipTests
	java -jar mosaic-api/target/mosaic-api-0.1.0-SNAPSHOT.jar

analysis-worker:
	./mvnw -pl mosaic-analysis-worker -am package -DskipTests
	java -jar mosaic-analysis-worker/target/mosaic-analysis-worker-0.1.0-SNAPSHOT.jar

render-worker:
	./mvnw -pl mosaic-render-worker -am package -DskipTests
	java -jar mosaic-render-worker/target/mosaic-render-worker-0.1.0-SNAPSHOT.jar

render-worker-docker:
	docker compose --profile workers up -d --build render-worker

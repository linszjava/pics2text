.PHONY: verify package infra-up infra-down infra-status api analysis-worker render-worker

verify:
	./mvnw verify

package:
	./mvnw clean package

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

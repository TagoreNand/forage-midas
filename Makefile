# Midas Core — developer shortcuts.
.PHONY: help build test verify run up down logs image clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Compile
	./mvnw -B clean package -DskipTests

test: ## Run unit + architecture tests
	./mvnw -B test

verify: ## Full verify (unit + integration via Testcontainers)
	./mvnw -B verify

run: ## Run locally with the H2 dev profile
	./mvnw spring-boot:run -Dspring-boot.run.profiles=local

up: ## Start the full local topology (Kafka, Postgres, Redis, Prometheus, Grafana)
	docker compose up -d --build

down: ## Stop the local topology
	docker compose down

logs: ## Tail the application logs
	docker compose logs -f midas-core

image: ## Build the container image
	docker build -t midas-core:local .

clean: ## Remove build artifacts
	./mvnw -B clean

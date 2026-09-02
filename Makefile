CLIENT := app/client

.PHONY: help setup test test-unit test-contract test-e2e verify run build fmt clean

help:
	@echo "setup          install client dependencies (npm ci)"
	@echo "test-unit      fast tests: server + engine JVM suites, client vitest"
	@echo "test-contract  client decoders vs a self-booted backend (slow first run)"
	@echo "test-e2e       Playwright browser suite (boots backend + vite)"
	@echo "verify         everything: unit + contract + lint + client build + format check"
	@echo "run            start the integrated app (server builds and serves the client)"
	@echo "build          produce the self-contained app/server/build/libs/dbest-*.jar"
	@echo "fmt            format the client with prettier"
	@echo "clean          drop all build output"

setup:
	cd $(CLIENT) && npm ci

test: test-unit

test-unit:
	./gradlew check
	cd $(CLIENT) && npm run test:unit

test-contract:
	cd $(CLIENT) && npm run test:contract

test-e2e:
	cd $(CLIENT) && npm run test:e2e

verify: test-unit test-contract
	cd $(CLIENT) && npm run lint && npm run build && npm run format:check

run:
	./gradlew :app:server:run

build:
	./gradlew :app:server:bundledJar

fmt:
	cd $(CLIENT) && npm run format

clean:
	./gradlew clean
	rm -rf $(CLIENT)/dist

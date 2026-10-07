CLIENT := app/client

.PHONY: help test test-unit test-e2e verify run build fmt clean

help:
	@echo "test-unit      fast tests: server + engine JVM suites, client vitest"
	@echo "test-e2e       Playwright browser suite (boots backend + vite)"
	@echo "verify         unit + lint + client build + format check (no browser suite)"
	@echo "run            build and start the integrated app"
	@echo "build          produce the self-contained build/libs/dbest-*.jar"
	@echo "fmt            format the client (prettier) and the server (ktfmt)"
	@echo "clean          drop all build output"

test: test-unit

test-unit:
	./gradlew :check :app:client:npmInstall
	cd $(CLIENT) && npm run test:unit

test-e2e:
	./gradlew :app:client:npmInstall
	cd $(CLIENT) && npm run test:e2e

verify: test-unit
	cd $(CLIENT) && npm run lint && npm run format:check
	./gradlew :app:client:build

run:
	./gradlew :run

build:
	./gradlew :bundledJar

fmt:
	./gradlew :app:client:npmInstall :app:server:ktfmtFormat
	cd $(CLIENT) && npm run format

clean:
	./gradlew clean

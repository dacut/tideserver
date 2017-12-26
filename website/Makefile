include override.mk

S3_PREFIX ?=
TSC_FLAGS = --target ES2016

TS_SOURCE = js/tideserver.ts
JS_GENERATED = $(TS_SOURCE:%.ts=%.js)

js/%.js: js/%.ts
	tsc $(TSC_FLAGS) --outFile $@ $<

deploy: $(JS_GENERATED)
	aws $(AWS_CLI_FLAGS) s3 sync . s3://$(S3_BUCKET)/$(S3_PREFIX) --exclude Makefile --exclude override.mk --exclude "*.ts"

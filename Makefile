
build:
	./gradlew build
	docker build -t az82/mn-upload .

deploy:
	kubectl apply -f k8s/deployment.yaml
	kubectl apply -f k8s/service.yaml
	kubectl apply -f k8s/ingress.yaml

undeploy:
	kubectl delete -f k8s/deployment.yaml
	kubectl delete -f k8s/service.yaml
	kubectl delete -f k8s/ingress.yaml

clean: undeploy
	docker rmi -f az82/mn-upload
	./gradlew clean

.PHONY: build clean deploy
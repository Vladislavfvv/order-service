# order-service

Third part of Spring Project

Пересобрать проект: 
mvn clean package

Пересобрать Docker образ:
docker-compose up -d --build

Собрать образ в Minikube Docker daemon:
cd ..\order-service\order-service; docker build -t order-service:latest .

Перезапуск deployment в Kubernetes
kubectl rollout restart deployment/order-service -n microservices

Перезапуск подов
kubectl get pods -n microservices -l app=order-service

Логи
kubectl logs -n microservices -l app=order-service --tail=50
kubectl logs -n microservices -l app=order-service --tail=100 | Select-String -Pattern "order|Order" | Select-Object -Last 20

Создать новый заказ:
POST http://localhost:9090/api/v1/orders
   Authorization: Bearer <токен>
   {
       "items": [
           {
               "itemId": 1,
               "quantity": 2
           }
       ]
   }

Посмотреть заказы по id 1, 2, 7
curl -X GET "http://localhost:9090/api/v1/orders/ids?ids=1&ids=2&ids=7" \
  -H "Authorization: Bearer <ваш_токен>"

Вывести из БД заказы
kubectl exec order-postgres-0 -n microservices -- psql -U postgres -d postgres -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE '%order%';"




Пересобрать образ в Minikube (одна команда  1 строка - Переключение на Minikube Docker daemon, 2 строка переключение, 
3 строка - Сборка образа в Minikube):
cd D:\JAVA\Innowise\Projects\28-11-2025\kubernetes
   minikube docker-env | Invoke-Expression 
   cd ..\order-service\order-service
   docker build -t order-service:latest .

Перезапустить deployment
   kubectl rollout restart deployment/order-service -n microservices

Проверить статус сервиса
   kubectl rollout status deployment/order-service -n microservices --timeout=5m

Повторить запрос PUT и проверить логи
   kubectl logs -n microservices -l app=order-service --tail=50 -f


   kubectl get pods -n microservices -l app=order-service


   kubectl get events -n microservices --sort-by='.lastTimestamp' | Select-String "order-service"

Масштабирование подов перед удалением. чтобы оставить только по одной:
kubectl scale statefulset order-postgres --replicas=1 -n microservices
kubectl scale statefulset user-postgres --replicas=1 -n microservices

После масштабирования StatefulSet автоматически удалил лишние поды (начиная с самого большого индекса).

Удаление подов:
   kubectl delete pod order-postgres-1 -n microservices;

   kubectl apply + ручное удаление лишних подов (начиная с самого большого индекса)
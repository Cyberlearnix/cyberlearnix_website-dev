@echo off
start /b java -jar course-service/build/libs/course-service-1.0.0-SNAPSHOT.jar > course2.log 2>&1
start /b java -jar enrollment-service/build/libs/enrollment-service-1.0.0-SNAPSHOT.jar > enroll2.log 2>&1
start /b java -jar notification-service/build/libs/notification-service-1.0.0-SNAPSHOT.jar > notif2.log 2>&1
start /b java -jar shop-service/build/libs/shop-service-1.0.0-SNAPSHOT.jar > shop2.log 2>&1
start /b java -jar user-service/build/libs/user-service-1.0.0-SNAPSHOT.jar > user2.log 2>&1
start /b java -jar gateway-service/build/libs/gateway-service-1.0.0-SNAPSHOT.jar > gateway2.log 2>&1

@echo off
REM 初始化 Redis 店铺地理位置数据
echo Loading shop GEO data into Redis...
docker exec dianpingplus-mysql mysql -uroot -proot123 dianpingplus_db -N -e "SELECT CONCAT('GEOADD shop:geo:', type_id, ' ', x, ' ', y, ' ', id) FROM tb_shop" 2>nul | docker exec -i dianpingplus-redis redis-cli -n 1
echo Done.

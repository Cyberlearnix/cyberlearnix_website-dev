import re
with open('/home/swachvegadev/cyberlearnix/docker-compose.yml','r') as f:
    content = f.read()
# Fix postgres port
content = re.sub(r'127\.0\.0\.1:0\.0\.0\.0:5432:5432', '0.0.0.0:5432:5432', content)
content = re.sub(r'"5999:5432"', '"0.0.0.0:5432:5432"', content)
# Fix redis port
content = re.sub(r'127\.0\.0\.1:6379:6379', '0.0.0.0:6379:6379', content)
content = re.sub(r'"6379:6379"', '"0.0.0.0:6379:6379"', content)
with open('/home/swachvegadev/cyberlearnix/docker-compose.yml','w') as f:
    f.write(content)
print('Fixed ports in docker-compose.yml')

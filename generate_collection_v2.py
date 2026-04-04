import os
import re
import json
import uuid

BACKEND_DIR = r"c:\Users\sridh\OneDrive\Desktop\cyberlearnix_website (1)\cyberlearnix_website\backend"

SERVICE_PORTS = {
    "gateway-service": 8080,
    "user-service": 8081,
    "course-service": 8082,
    "enrollment-service": 8083,
    "notification-service": 8084,
    "shop-service": 8085,
    "form-service": 8087,
    "cms-service": 8089,
    "admin-service": 8090
}

def find_java_files(root_dir):
    java_files = []
    for root, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".java"):
                java_files.append(os.path.join(root, file))
    return java_files

def get_sample_value(full_type, field_name=""):
    t = full_type.lower()
    fn = field_name.lower()
    
    if "email" in fn: return "admin@cyberlearnix.com"
    if "password" in fn: return "Password123!"
    if "url" in fn or "img" in fn: return "https://example.com/image.png"
    if "date" in fn or "time" in fn or "at" in fn.split("_") or "offsetdatetime" in t or "localdatetime" in t: 
        return "2026-03-19T12:00:00Z"
    if "role" in fn: return "admin"
    if "otp" in fn: return "123456"
    if "token" in fn: return "sample-token-xyz"
    if ("id" in fn and ("string" in t or "uuid" in t)) or "uuid" in t: 
        return str(uuid.uuid4())
    
    if "string" in t: return "sample_text"
    if "long" in t or "int" in t or "integer" in t: return 123
    if "double" in t or "float" in t or "decimal" in t: return 10.5
    if "boolean" in t: return True
    if "list" in t or "set" in t: return ["sample_item"]
    if "map" in t: return {"key": "value"}
    if "object" in t: return "sample_object"
    return "null"

def parse_dto_fields(content, all_java_files, depth=0):
    if depth > 3: return {} # Prevent infinite recursion
    
    fields = {}
    # Match private/protected/public fields
    field_matches = re.findall(r"(?:private|protected|public)\s+([\w<>,\s]+)\s+(\w+);", content)
    for f_type, f_name in field_matches:
        f_type = f_type.strip()
        # Handle Generic types like List<SomeDTO> or Map<String, SomeDTO>
        if "Map<" in f_type:
            inner_type = f_type.split(",")[-1].replace(">", "").strip()
            if any(x in inner_type.lower() for x in ["string", "long", "int", "boolean", "double", "object"]):
                fields[f_name] = {"key": get_sample_value(inner_type, f_name)}
            else:
                inner_content = find_dto_content(inner_type, all_java_files)
                if inner_content:
                    fields[f_name] = {"key": parse_dto_fields(inner_content, all_java_files, depth + 1)}
                else:
                    fields[f_name] = {"key": "sample_object"}
        elif "List<" in f_type or "Set<" in f_type:
            inner_type = re.search(r'<(.*)>', f_type).group(1).strip()
            if any(x in inner_type.lower() for x in ["string", "long", "int", "boolean", "double", "object"]):
                fields[f_name] = [get_sample_value(inner_type, f_name)]
            else:
                inner_content = find_dto_content(inner_type, all_java_files)
                if inner_content:
                    fields[f_name] = [parse_dto_fields(inner_content, all_java_files, depth + 1)]
                else:
                    fields[f_name] = ["sample_object"]
        else:
            sample = get_sample_value(f_type, f_name)
            if sample == "null" and not any(x in f_type.lower() for x in ["string", "long", "int", "boolean", "double"]):
                # Likely another DTO
                inner_dto_content = find_dto_content(f_type, all_java_files)
                if inner_dto_content:
                    fields[f_name] = parse_dto_fields(inner_dto_content, all_java_files, depth + 1)
                else:
                    fields[f_name] = sample
            else:
                fields[f_name] = sample
    return fields

def find_dto_content(dto_name, all_java_files):
    # Basic check for common names or partial paths
    for f in all_java_files:
        if os.path.basename(f) == f"{dto_name}.java":
            with open(f, "r", encoding="utf-8") as file:
                return file.read()
    return None

def generate_collection():
    all_files = find_java_files(BACKEND_DIR)
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": "Cyberlearnix API Collection",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "item": []
    }

    folders = {}

    for file_path in all_files:
        if "test" in file_path.lower(): continue
        
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()
                if "@RestController" not in content and "@Controller" not in content:
                    continue

                service_name = "unknown"
                for s in SERVICE_PORTS:
                    if s in file_path:
                        service_name = s
                        break
                
                if service_name not in folders:
                    folders[service_name] = {
                        "name": service_name + f" (Port {SERVICE_PORTS.get(service_name, '???')})",
                        "item": []
                    }
                    collection["item"].append(folders[service_name])

                base_path = ""
                base_match = re.search(r'@RequestMapping\("([^"]+)"\)', content)
                if base_match:
                    base_path = base_match.group(1)

                # Capture HTTP methods
                mappings = re.findall(r'@(Get|Post|Put|Delete|Patch)Mapping(\(.*?\))?\s+.*?public\s+.*?\s+(\w+)\s*\((.*?)\)', content, re.DOTALL)
                
                for m_type, m_anno, m_name, m_params in mappings:
                    m_path = ""
                    if m_anno:
                        path_match = re.search(r'(?:value\s*=\s*)?"([^"]*)"', m_anno)
                        if path_match:
                            m_path = path_match.group(1)
                    
                    full_path = (base_path + m_path).replace("//", "/")
                    
                    # Request Body
                    body_content = ""
                    body_match = re.search(r'@RequestBody\s+([\w<>,\s]+)\s+(\w+)', m_params)
                    if body_match:
                        body_type = body_match.group(1).strip()
                        if "Map" in body_type:
                            method_start = content.find(m_name + "(")
                            snippet = content[method_start:method_start+5000]
                            keys = re.findall(r'\.get\("([^"]+)"\)', snippet)
                            keys = list(set(keys))
                            if keys:
                                body_dict = {k: "sample_value" for k in keys}
                                body_content = json.dumps(body_dict, indent=4)
                            else:
                                body_content = "{\n  \"key\": \"value\"\n}"
                        else:
                            dto_content = find_dto_content(body_type, all_files)
                            if dto_content:
                                fields = parse_dto_fields(dto_content, all_files)
                                body_content = json.dumps(fields, indent=4)
                            else:
                                # Look for inner class
                                inner_match = re.search(rf'class\s+{body_type}\s+{{(.*?)\}}', content, re.DOTALL)
                                if inner_match:
                                    fields = parse_dto_fields(inner_match.group(1), all_files)
                                    body_content = json.dumps(fields, indent=4)
                                else:
                                    body_content = "{\n  \"info\": \"DTO not found, please check fields manually\"\n}"

                    # Query Params & Path Variables
                    query_params = []
                    qp_matches = re.findall(r'@RequestParam.*?\s+([\w<>,\s]+)\s+(\w+)', m_params)
                    for p_type, p_name in qp_matches:
                        query_params.append({"key": p_name, "value": str(get_sample_value(p_type, p_name))})

                    item = {
                        "name": f"[{m_type.upper()}] {full_path} ({m_name})",
                        "request": {
                            "method": m_type.upper(),
                            "header": [
                                {"key": "Authorization", "value": "Bearer {{admin_token}}", "type": "text"},
                                {"key": "Content-Type", "value": "application/json", "type": "text"}
                            ],
                            "description": f"Service: {service_name}\nMethod: {m_name}",
                            "url": {
                                "raw": f"{{{{base_url}}}}:{SERVICE_PORTS.get(service_name, 0)}{full_path}",
                                "host": ["{{base_url}}"],
                                "port": str(SERVICE_PORTS.get(service_name, 0)),
                                "path": full_path.strip("/").split("/"),
                                "query": query_params
                            }
                        }
                    }
                    if body_content:
                        item["request"]["body"] = {
                            "mode": "raw",
                            "raw": body_content
                        }
                    
                    folders[service_name]["item"].append(item)
        except Exception as e:
            print(f"Error processing {file_path}: {e}")

    # Sort
    for s in folders:
        folders[s]["item"].sort(key=lambda x: "/".join(x["request"]["url"]["path"]))

    # Add Gateway routes as a special folder if needed, but the above covers all services directly
    
    # Save
    output_path = os.path.join(BACKEND_DIR, "Master_API_Collection.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(collection, f, indent=4)
    print(f"Collection generated successfully at {output_path}")

if __name__ == "__main__":
    generate_collection()

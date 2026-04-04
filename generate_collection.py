import os
import re
import json
import uuid

BACKEND_DIR = r"c:\Users\sridh\OneDrive\Desktop\cyberlearnix_website (1)\cyberlearnix_website\backend"

# Service Map for URL generation
SERVICE_PORTS = {
    "gateway-service": 8080,
    "user-service": 8081,
    "course-service": 8082,
    "enrollment-service": 8083,
    "notification-service": 8084,
    "shop-service": 8085,
    "form-service": 8087,
    "cms-service": 8089
}

def find_java_files(root_dir):
    java_files = []
    for root, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".java"):
                java_files.append(os.path.join(root, file))
    return java_files

def parse_class_fields(file_path):
    fields = {}
    if not os.path.exists(file_path):
        return fields
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
        # Look for private/protected fields
        field_matches = re.findall(r"(?:private|protected|public)\s+([\w<>,\s]+)\s+(\w+);", content)
        for f_type, f_name in field_matches:
            fields[f_name] = f_type.strip()
    return fields

def get_sample_value(full_type):
    t = full_type.lower()
    if "string" in t: return "sample_text"
    if "long" in t or "int" in t or "integer" in t: return 123
    if "double" in t or "float" in t or "decimal" in t: return 10.5
    if "boolean" in t: return True
    if "list" in t or "set" in t: return []
    if "map" in t: return {}
    if "date" in t or "time" in t: return "2026-03-19T12:00:00Z"
    return "null"

def resolve_dto(dto_name, all_java_files):
    # Try to find the file for this DTO (considering it might be a simple name or partial path)
    for f in all_java_files:
        if os.path.basename(f) == f"{dto_name}.java":
            return parse_class_fields(f)
    return {}

def generate_collection():
    all_files = find_java_files(BACKEND_DIR)
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": "Master API Collection",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "item": []
    }

    folders = {}

    for file_path in all_files:
        if "test" in file_path.lower(): continue
        
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
            if "@RestController" not in content and "@Controller" not in content:
                continue

            # Identify Service
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

            # Base Path
            base_path = ""
            base_match = re.search(r'@RequestMapping\("([^"]+)"\)', content)
            if base_match:
                base_path = base_match.group(1)

            # Find Methods (supporting various mapping annotations)
            methods = re.findall(r'@(Get|Post|Put|Delete|Patch)Mapping\((?:value\s*=\s*)?"([^"]*)"(?:,.*?)?\)\s+.*?public\s+.*?\s+(\w+)\s*\((.*?)\)', content, re.DOTALL)
            # Also catch mappings with NO value (empty string)
            methods_empty = re.findall(r'@(Get|Post|Put|Delete|Patch)Mapping\s+public\s+.*?\s+(\w+)\s*\((.*?)\)', content, re.DOTALL)
            
            # Combine them
            processed_methods = []
            for t, p, n, params in methods: processed_methods.append((t, p, n, params))
            for t, n, params in methods_empty: processed_methods.append((t, "", n, params))

            for m_type, m_path, m_name, m_params in processed_methods:
                full_path = (base_path + m_path).replace("//", "/")
                
                # Request Body
                body_content = ""
                body_match = re.search(r'@RequestBody\s+([\w<>,\s]+)\s+(\w+)', m_params)
                if body_match:
                    body_type = body_match.group(1).strip()
                    if "Map" in body_type:
                        # Infer Map keys from method body
                        method_start = content.find(m_name + "(")
                        snippet = content[method_start:method_start+3000] # deeper snippet
                        keys = re.findall(r'\.get\("([^"]+)"\)', snippet)
                        # Also look for bracket access in some contexts if any
                        keys = list(set(keys))
                        if keys:
                            body_dict = {k: "sample_value" for k in keys}
                            body_content = json.dumps(body_dict, indent=4)
                        else:
                            body_content = "{\n  \"key\": \"value\"\n}"
                    else:
                        # Resolve DTO
                        fields = resolve_dto(body_type, all_files)
                        if fields:
                            body_dict = {k: get_sample_value(v) for k, v in fields.items()}
                            body_content = json.dumps(body_dict, indent=4)
                        else:
                            # Try to find class definition in same file if it's an inner class
                            inner_class_match = re.search(rf'class\s+{body_type}\s+{{(.*?)\}}', content, re.DOTALL)
                            if inner_class_match:
                                inner_content = inner_class_match.group(1)
                                field_matches = re.findall(r"private\s+([\w<>,\s]+)\s+(\w+);", inner_content)
                                if field_matches:
                                    body_dict = {n: get_sample_value(t) for t, n in field_matches}
                                    body_content = json.dumps(body_dict, indent=4)
                                else:
                                    body_content = "{\n  \"inner_class\": \"found_but_no_fields\"\n}"
                            else:
                                body_content = "{\n  \"raw_body\": \"could_not_parse_dto\"\n}"

                # Query Params
                query_params = []
                param_matches = re.findall(r'@RequestParam.*?\s+([\w<>,\s]+)\s+(\w+)', m_params)
                for p_type, p_name in param_matches:
                    query_params.append({"key": p_name, "value": str(get_sample_value(p_type))})

                item = {
                    "name": f"[{m_type.upper()}] {full_path} ({m_name})",
                    "request": {
                        "method": m_type.upper(),
                        "header": [
                            {"key": "Authorization", "value": "Bearer {{admin_token}}", "type": "text"},
                            {"key": "Content-Type", "value": "application/json", "type": "text"}
                        ],
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

    # Sort items by path
    for s in folders:
        folders[s]["item"].sort(key=lambda x: "/".join(x["request"]["url"]["path"]))

    with open(os.path.join(BACKEND_DIR, "Master_API_Collection.json"), "w", encoding="utf-8") as f:
        json.dump(collection, f, indent=4)
    print("Collection updated successfully!")

if __name__ == "__main__":
    generate_collection()

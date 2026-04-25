{{/*
Cyberlearnix Helm helpers
*/}}

{{- define "cyberlearnix.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "cyberlearnix.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Standard labels applied to every resource
Usage: include "cyberlearnix.labels" (dict "serviceName" $name "root" $)
*/}}
{{- define "cyberlearnix.labels" -}}
app.kubernetes.io/name: {{ .serviceName }}
app.kubernetes.io/part-of: cyberlearnix
app.kubernetes.io/managed-by: Helm
app.kubernetes.io/version: {{ index .root.Values.images .serviceName | default "latest" | quote }}
helm.sh/chart: {{ include "cyberlearnix.chart" .root }}
environment: {{ .root.Values.global.environment }}
{{- end }}

{{/*
Selector labels (immutable — used by Deployment.spec.selector)
*/}}
{{- define "cyberlearnix.selectorLabels" -}}
app.kubernetes.io/name: {{ .serviceName }}
app.kubernetes.io/part-of: cyberlearnix
{{- end }}

{{/*
Full image reference: registry/[imagePrefix]service:tag
imagePrefex is empty by default; set to "cyberlearnix-" for Docker Hub naming.
*/}}
{{- define "cyberlearnix.image" -}}
{{- $tag := index .root.Values.images .serviceName | default "latest" -}}
{{- $prefix := .root.Values.global.imagePrefix | default "" -}}
{{- printf "%s/%s%s:%s" .root.Values.global.registry $prefix .serviceName $tag }}
{{- end }}

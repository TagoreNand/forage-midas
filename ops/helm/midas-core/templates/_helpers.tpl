{{- define "midas-core.name" -}}midas-core{{- end -}}
{{- define "midas-core.labels" -}}
app.kubernetes.io/name: {{ include "midas-core.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

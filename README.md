# Consulta de cuentas y saldos

Proyecto de consulta de cuentas y saldos en **Java 21, Spring Boot 4.1.1 y Maven**. Hay dos servicios HTTP independientes:

```text
Canal --> ms-business (busaccbalv1) --> ms-core (simcoreaccv1) --> DynamoDB Local
```

Los contratos OpenAPI [de negocio](openapi/business.yaml) y [del core](openapi/core.yaml) describen las interfaces de los servicios.

## Organización del código

Cada módulo tiene su propia clase de arranque en el paquete raíz para que Spring detecte los componentes de sus subpaquetes.

| Módulo | Paquetes y responsabilidad |
| --- | --- |
| `ms-business` | `api` expone la respuesta HTTP; `application` valida la respuesta del core, mapea códigos y calcula totales; `integration.core` realiza la llamada HTTP y representa los datos recibidos; `config` configura los mensajes. |
| `ms-core` | `api` atiende la consulta; `model` representa clientes y cuentas; `persistence` accede a DynamoDB Local; `simulation` reproduce fallas en el perfil `local`. |

`CoreClient` concentra la petición HTTP al core. `AccountsService` decide cómo traducir sus errores al API de negocio. Esta separación mantiene las responsabilidades visibles sin añadir capas que solo deleguen llamadas.

## Requisitos y arranque desde cero

En una laptop Windows nueva, descargue el proyecto (con Git o como ZIP) e instale [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/). Debe estar en ejecución con contenedores Linux; compruebe los requisitos de Windows, WSL 2 y virtualización indicados por Docker. Docker Desktop incluye Docker Engine y Compose, por lo que no hace falta instalarlos por separado.

El primer arranque necesita conexión a internet para descargar las imágenes Docker y las dependencias Maven, además de los puertos `3000`, `3001` y `8000` libres. **No hace falta instalar Java ni Maven en Windows para levantar los servicios:** el `Dockerfile` usa Maven y Java 21 dentro de las imágenes. Para ejecutar `./mvnw.cmd test` fuera de Docker sí se requiere Java 21 en el equipo.

```sh
docker compose up --build -d
docker compose ps
```

El core crea la tabla `ClientesCuentas` y carga los dos clientes existentes al iniciar. Espere a que la consulta de cuentas responda antes de probarla. Los puertos se publican solo en `127.0.0.1`. DynamoDB Local usa memoria: los datos se vuelven a cargar al crear un contenedor nuevo.

Para detener la solución:

```sh
docker compose down
```

## Consultas de ejemplo

Desde Windows PowerShell, use `curl.exe -i` para ver también el estado HTTP:

```powershell
curl.exe -i 'http://127.0.0.1:3000/v1/customers/12345678/accounts'
curl.exe -i 'http://127.0.0.1:3000/v1/customers/12345678/accounts?currency=PEN'
curl.exe -i 'http://127.0.0.1:3000/v1/customers/87654321/accounts'
curl.exe -i -H 'Accept-Language: es' 'http://127.0.0.1:3000/v1/customers/99999999/accounts'
```

Las respuestas esperadas son, en orden: HTTP 200 con tres cuentas y totales `PEN=1450.00` y `USD=300.00`; HTTP 200 con dos cuentas PEN y total `1450.00`; HTTP 200 con `accounts: []`; y HTTP 404 con el mensaje `Cliente no encontrado`. La cuenta bloqueada se muestra, pero no se suma.

### Probar también en Postman

Con la solución levantada mediante Compose, importe la [colección Postman](postman/PruebaTecnica.postman_collection.json) en Postman. La colección incluye las consultas anteriores, el rechazo de moneda inválida y una secuencia para simular lentitud, indisponibilidad y error. Puede enviar cada petición o ejecutar la colección completa en el orden mostrado y revisar sus resultados de pruebas.

Las variables de la colección apuntan a `http://127.0.0.1:3000` (negocio) y `http://127.0.0.1:3001` (core); modifíquelas si usa otros puertos. La primera petición pone la simulación en modo `normal`. Al final de la sección de fallas, `Activar normal` restaura ese modo antes de verificar la recuperación; envíela manualmente si interrumpe la ejecución. Las peticiones de simulación requieren el perfil Spring `local`, que Compose ya activa. Mantenga el tiempo de espera del cliente Postman por encima de 2 segundos para que la prueba de lentitud reciba el HTTP 504 emitido por negocio.

Para probar las fallas, ejecute estas órdenes en la misma sesión de PowerShell. Después de cada cambio de modo, consulte la API de negocio y compare el estado indicado:

```powershell
$sim = 'http://127.0.0.1:3001/__admin/simulation'
$accounts = 'http://127.0.0.1:3000/v1/customers/12345678/accounts'

Invoke-RestMethod -Uri $sim -Method Post -ContentType 'application/json' -Body (@{ mode = 'slow'; delayMs = 2000 } | ConvertTo-Json)
curl.exe -i $accounts  # HTTP 504: timeout del core

Invoke-RestMethod -Uri $sim -Method Post -ContentType 'application/json' -Body (@{ mode = 'unavailable' } | ConvertTo-Json)
curl.exe -i $accounts  # HTTP 503: core no disponible

Invoke-RestMethod -Uri $sim -Method Post -ContentType 'application/json' -Body (@{ mode = 'error' } | ConvertTo-Json)
curl.exe -i $accounts  # HTTP 502: error del core

Invoke-RestMethod -Uri $sim -Method Post -ContentType 'application/json' -Body (@{ mode = 'normal' } | ConvertTo-Json)
```

Ejecute la última orden para restaurar el modo `normal`, incluso si interrumpe la secuencia. El modo `unavailable` devuelve HTTP 503 desde el core; no detiene su proceso. La ruta de simulación solo existe con el perfil Spring `local`, activado en Compose. Es una herramienta de prueba sin autenticación y no debe habilitarse en producción.

## Pruebas sin contenedores

Se requiere Java 21. El repositorio incluye Maven Wrapper; Maven global no es necesario.

```powershell
./mvnw.cmd test
```

Las pruebas verifican el mapeo y la suma, el cliente vacío, códigos inesperados, la validación de moneda, los mensajes en inglés y español, la traducción de errores HTTP y el timeout. También comprueban las respuestas del core en los modos `unavailable` y `error`. Los servicios se empaquetan con `./mvnw.cmd package`.

## Decisiones principales

| Tema | Decisión y motivo |
| --- | --- |
| Separación | `ms-business` no accede a DynamoDB. Todo dato pasa por el contrato HTTP del core. |
| Filtro | `currency=PEN` o `USD` filtra tanto cuentas como totales. Sin filtro se muestran las monedas presentes. |
| Dinero | El core expone números JSON como en el enunciado. Negocio usa `BigDecimal` para operar y entrega cadenas con dos decimales, evitando cambios de escala o redondeos implícitos en los canales. |
| Cuentas bloqueadas | Se muestran para que el canal conozca su estado; el total solo suma saldos disponibles de cuentas activas. |
| Idioma | Campos y códigos del negocio siempre en inglés. `Accept-Language` elige mensajes `en` o `es`; el texto español del core no se transmite al canal. |
| Fallas | 404 para cliente ausente; 503 para core indisponible; 504 para timeout; 502 para error o respuesta inválida del core. |
| Persistencia | DynamoDB Local guarda un elemento por cliente, con cuentas como lista tipada. La carga condicional no sobreescribe datos al reiniciar el core. |
| Seguimiento | Cada respuesta de negocio contiene `requestId` y la cabecera `X-Request-Id`, que también se envía al core. Los errores de conexión, las respuestas HTTP inesperadas y los datos inválidos del core incluyen ese identificador en los logs de negocio. |

## Evolución para producción

- **Seguridad de la consulta:** autenticar a quien llama a la API y verificar que tenga permiso para consultar el `customerId` solicitado.
- **Monitoreo y disponibilidad:** registrar el mismo `requestId` en los logs de ambos servicios y medir cuánto tardan y cuántas veces fallan las consultas al core. El `/health` actual solo confirma que el servicio responde; en producción agregaría una comprobación que indique si está preparado para atender consultas, por ejemplo, después de terminar su inicialización.
- **Arquitectura ante el crecimiento:** si se incorporan varios canales con necesidades distintas o cambian las tecnologías de integración y persistencia, evolucionar la organización actual hacia una arquitectura hexagonal. Los casos de uso y las reglas de saldos quedarían detrás de puertos; los controladores, el cliente HTTP y la persistencia serían adaptadores reemplazables. Para el alcance actual, la separación por responsabilidades es suficiente.

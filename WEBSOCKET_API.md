# WebSocket API для электронной очереди

## Подключение

**WebSocket URL:** `ws://localhost:8085/queue-websocket`  
**SockJS URL:** `http://localhost:8085/queue-websocket` (с SockJS fallback)

**Порт:** `8085`

## Авторизация

При подключении к WebSocket необходимо передать JWT токен одним из способов:

### Способ 1: В заголовке STOMP
```
Authorization: Bearer <your_jwt_token>
```

### Способ 2: В query параметре URL
```
ws://localhost:8085/queue-websocket?token=<your_jwt_token>
```

## Структура сообщений

### Префиксы:
- **Отправка на сервер:** `/app/queue/*`
- **Получение от сервера:** `/queue/user`

## Эндпоинты

### 1. Инициализация очереди

**Отправка:**
```json
{
  "destination": "/app/queue/init"
}
```

**Ответ:**
```json
{
  "success": true,
  "message": "Очередь успешно построена",
  "data": [
    {
      "id": 1,
      "doctorId": 1,
      "appointmentId": 5,
      "patientId": 1,
      "position": 0,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

**Описание:**
- Автоматически находит все appointments пользователя с заполненным `patient_id`
- Фильтрует прошедшие записи: если сейчас на 20+ минут больше времени приема, запись пропускается
- Исключает appointments со статусами `'completed'` и `'cancelled'`
- Строит очередь для каждого врача отдельно
- Позиции учитывают всех пациентов в очереди к врачу (отсортированы по времени appointments)

---

### 2. Получить позицию в очереди

**Отправка:**
```json
{
  "destination": "/app/queue/position",
  "body": {
    "doctorId": 1
  }
}
```

**Ответ:**
```json
{
  "success": true,
  "message": "Позиция в очереди получена",
  "data": {
    "queueEntryId": 1,
    "doctorId": 1,
    "patientId": 1,
    "position": 0,
    "isNext": true,
    "message": "Вы следующий в очереди"
  }
}
```

**Поля ответа:**
- `queueEntryId` (Long) - ID записи в очереди
- `doctorId` (Long) - ID врача
- `patientId` (Long) - ID пациента
- `position` (Integer) - Позиция в очереди (0 - первый в очереди)
- `isNext` (Boolean) - Является ли пользователь следующим в очереди (если нет записей перед текущей, значит текущий пользователь следующий)
- `message` (String) - Сообщение о статусе

---

### 3. Получить все очереди пользователя

**Отправка:**
```json
{
  "destination": "/app/queue/my-queues"
}
```

**Ответ:**
```json
{
  "success": true,
  "message": "Очереди получены",
  "data": [
    {
      "id": 1,
      "doctorId": 1,
      "appointmentId": 5,
      "patientId": 1,
      "position": 0,
      "lastUpdated": "2024-01-01T12:00:00Z"
    },
    {
      "id": 2,
      "doctorId": 2,
      "appointmentId": 6,
      "patientId": 1,
      "position": 1,
      "lastUpdated": "2024-01-01T13:00:00Z"
    }
  ]
}
```

---

## Пример подключения (JavaScript)

```javascript
// Используя SockJS и STOMP
const socket = new SockJS('http://localhost:8085/queue-websocket');
const stompClient = Stomp.over(socket);

// Подключение с токеном
const token = 'your_jwt_token_here';
stompClient.connect(
  {
    Authorization: `Bearer ${token}`
  },
  function(frame) {
    console.log('Connected: ' + frame);
    
    // Подписываемся на ответы (Spring автоматически добавляет /user/ префикс)
    stompClient.subscribe('/user/queue/user', function(message) {
      const data = JSON.parse(message.body);
      console.log('Received:', data);
    });
    
    // Инициализируем очередь
    stompClient.send('/app/queue/init', {}, JSON.stringify({}));
  },
  function(error) {
    console.log('Error: ' + error);
  }
);
```

## Пример подключения (Android/Kotlin)

```kotlin
// Используя OkHttp WebSocket
val request = Request.Builder()
    .url("ws://localhost:8085/queue-websocket?token=$jwtToken")
    .build()

val webSocket = client.newWebSocket(request, object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Отправляем сообщение для инициализации
        val initMessage = """
            {
                "destination": "/app/queue/init",
                "body": {}
            }
        """.trimIndent()
        webSocket.send(initMessage)
    }
    
    override fun onMessage(webSocket: WebSocket, text: String) {
        // Обрабатываем ответ
        val response = Gson().fromJson(text, QueueResponse::class.java)
    }
})
```

## Обработка ошибок

Все ответы содержат поле `success`:
- `success: true` - операция успешна
- `success: false` - произошла ошибка, поле `message` содержит описание ошибки

**Пример ошибки:**
```json
{
  "success": false,
  "message": "Пользователь не авторизован",
  "data": null
}
```

## Логика работы очереди

1. **При подключении** автоматически вызывается `/app/queue/init`, который:
   - Находит все appointments пользователя с `patient_id != null`
   - Фильтрует прошедшие записи (если сейчас на 20+ минут больше времени приема)
   - Исключает записи со статусами `'completed'` и `'cancelled'`
   - Строит очередь для каждого врача
   - Рассчитывает позиции с учетом всех пациентов к врачу

2. **Позиции в очереди:**
   - Позиция 0 = первый в очереди
   - Позиции рассчитываются на основе времени appointments всех пациентов к врачу
   - Если нет записи перед текущей, значит текущий пользователь следующий (`isNext: true`)

3. **Статусы appointments:**
   - Учитываются: `'scheduled'`, `'confirmed'`, `'in_progress'`, `'no_show'`
   - Исключаются: `'completed'`, `'cancelled'`


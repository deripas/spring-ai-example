// Markdown -> HTML с очисткой от опасной разметки (ответ LLM может содержать <script>, onerror= и т.п.)
function renderMarkdown(markdown) {
    return DOMPurify.sanitize(marked.parse(markdown));
}

document.addEventListener("DOMContentLoaded", function() {
    const sendButton = document.getElementById("send-button");
    const chatInput = document.getElementById("chat-input");
    const messagesContainer = document.getElementById("messages");

    // История из БД приходит как текст (th:text) - рендерим так же, как при стриме
    messagesContainer.querySelectorAll(".message.mentor .bubble").forEach(bubble => {
        bubble.innerHTML = renderMarkdown(bubble.textContent);
    });
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    sendButton.addEventListener("click", function() {
        const prompt = chatInput.value;
        if (!prompt) return;
        chatInput.value = "";

        // Добавляем сообщение пользователя в чат
        const userDiv = document.createElement("div");
        userDiv.className = "message user";
        userDiv.innerHTML = `<img src="/images/user.png" alt="User"><div class="bubble"></div>`;
        userDiv.querySelector(".bubble").textContent = prompt;
        messagesContainer.appendChild(userDiv);

        const pathParts = window.location.pathname.split("/");
        const chatId = pathParts[pathParts.length - 1];
        const url = `/chat-stream/${chatId}?prompt=${encodeURIComponent(prompt)}`;

        const eventSource = new EventSource(url);
        let fullText = "";

        // Создаем блок для ответа AI
        const aiDiv = document.createElement("div");
        aiDiv.className = "message mentor";
        // Добавляем изображение ассистента
        aiDiv.innerHTML = `<img src="/images/mentor.png" alt="Mentor">`;
        // Создаем элемент для содержимого, куда будем вставлять ответ
        const aiBubble = document.createElement("div");
        aiBubble.className = "bubble";
        aiDiv.appendChild(aiBubble);
        messagesContainer.appendChild(aiDiv);

        eventSource.onmessage = function(event) {
            const data = JSON.parse(event.data);
            let token = data.text;
            console.log(token);
            fullText += token;
            aiBubble.innerHTML = renderMarkdown(fullText);
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        };

        eventSource.onerror = function(e) {
            console.error("Ошибка SSE:", e);
            eventSource.close();
        };
    });
});

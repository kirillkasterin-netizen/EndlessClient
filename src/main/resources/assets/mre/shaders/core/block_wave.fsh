#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy * 0.01;
    
    // Волновые паттерны
    float wave1 = sin(uv.x * 10.0 + Time * 3.0);
    float wave2 = cos(uv.y * 10.0 + Time * 2.5);
    float wave3 = sin((uv.x + uv.y) * 8.0 + Time * 4.0);
    
    // Комбинируем волны
    float waves = (wave1 + wave2 + wave3) / 3.0;
    
    // Создаем градиент на основе волн
    vec3 color1 = vec3(0.0, 0.5, 1.0); // Голубой
    vec3 color2 = vec3(0.0, 1.0, 0.8); // Бирюзовый
    vec3 color3 = vec3(0.5, 0.0, 1.0); // Фиолетовый
    
    float t = waves * 0.5 + 0.5;
    vec3 finalColor = mix(color1, color2, t);
    finalColor = mix(finalColor, color3, sin(Time * 1.5) * 0.3 + 0.3);
    
    // Добавляем свечение на гребнях волн
    float glow = pow(abs(waves), 3.0);
    finalColor += glow * vec3(1.0, 1.0, 1.0) * 0.5;
    
    // Пульсация прозрачности
    float alpha = 0.7 + sin(Time * 2.0) * 0.3;
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}

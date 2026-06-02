#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy * 0.005;
    
    // Расстояние от центра экрана
    vec2 center = vec2(400.0, 300.0); // Примерный центр
    float dist = length(uv - center * 0.005);
    
    // Создаем пульсирующие кольца
    float rings = sin(dist * 20.0 - Time * 5.0) * 0.5 + 0.5;
    
    // Основная пульсация
    float mainPulse = sin(Time * 3.0) * 0.5 + 0.5;
    
    // Энергетический эффект
    float energy = pow(rings, 2.0) * mainPulse;
    
    // Цветовая схема - от центра к краям
    vec3 color1 = vec3(1.0, 0.2, 0.8); // Розовый
    vec3 color2 = vec3(0.2, 0.8, 1.0); // Голубой
    vec3 color3 = vec3(1.0, 1.0, 0.3); // Желтый
    
    vec3 finalColor = mix(color1, color2, dist * 0.1);
    finalColor = mix(finalColor, color3, energy);
    
    // Добавляем яркое свечение
    float centerGlow = 1.0 - smoothstep(0.0, 2.0, dist);
    finalColor += centerGlow * vec3(1.0, 1.0, 1.0) * mainPulse * 0.5;
    
    // Добавляем внешнее свечение на кольцах
    finalColor += rings * vec3(0.5, 0.5, 1.0) * 0.3;
    
    // Пульсирующая прозрачность
    float alpha = 0.6 + mainPulse * 0.4;
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}

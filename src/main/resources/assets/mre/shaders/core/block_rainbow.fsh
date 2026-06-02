#version 150

in vec4 vertexColor;

uniform float Time;

out vec4 fragColor;

// HSV to RGB conversion
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy * 0.01;
    
    // Создаем радужный градиент
    float hue = fract(Time * 0.2 + uv.x * 0.5 + uv.y * 0.5);
    
    // Добавляем волновой эффект к насыщенности
    float saturation = 0.8 + sin(Time * 3.0 + uv.y * 5.0) * 0.2;
    
    // Пульсирующая яркость
    float brightness = 0.9 + sin(Time * 2.0) * 0.1;
    
    vec3 rainbowColor = hsv2rgb(vec3(hue, saturation, brightness));
    
    // Добавляем блики
    float sparkle = sin(uv.x * 20.0 + Time * 5.0) * sin(uv.y * 20.0 + Time * 4.0);
    sparkle = pow(max(sparkle, 0.0), 10.0) * 0.5;
    
    vec3 finalColor = rainbowColor + sparkle;
    
    // Анимированная прозрачность
    float alpha = 0.75 + sin(Time * 2.5) * 0.25;
    
    fragColor = vec4(finalColor, alpha) * vertexColor;
}

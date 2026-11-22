#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform PixelateConfig {
    float PixelSize;
};

out vec4 fragColor;

void main() {
    vec2 block = PixelSize / OutSize;
    vec2 uv = (floor(texCoord / block) + 0.5) * block;
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}

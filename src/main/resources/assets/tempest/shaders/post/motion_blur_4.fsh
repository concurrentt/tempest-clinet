#version 330

// Tempest motion blur, strength 4/9: blends this frame with the accumulated previous frames.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 cur = texture(InSampler, texCoord).rgb;
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, prev, 0.40), 1.0);
}

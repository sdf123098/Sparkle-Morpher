package net.minecraft.client.input;
public class CharacterEvent {
    public char codePoint;
    public int codepoint;
    public CharacterEvent(int codepoint) {
        this.codepoint = codepoint;
        this.codePoint = (char) codepoint;
    }
    public int codepoint() { return codepoint; }
    public String codepointAsString() { return String.valueOf((char)codepoint); }
}

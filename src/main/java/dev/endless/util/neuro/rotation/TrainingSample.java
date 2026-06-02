package dev.endless.util.neuro.rotation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSample {
    private float[] input;  
    private float[] output; 
}

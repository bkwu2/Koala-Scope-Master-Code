%% LMS IMPLEMENTATION
% SCRIPT FOR TESTING LMS ALGORITHM

clc; clear all; close all;
% Indicating directory where sound files are being stored
% Change folder depending on file location
Sound_Folder = dir('C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\L-H-3');
H_L = 'C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\L-H-3';

% Data paths for each sensor channel
primary_data = fullfile(H_L, Sound_Folder(3).name);
ecg_data = fullfile(H_L, Sound_Folder(4).name);
extref_data = fullfile(H_L, Sound_Folder(5).name);
intref_data = fullfile(H_L, Sound_Folder(6).name);

% Read in data 
[Main_1, Fs] = audioread(primary_data);
[ECG_1, Fs] = audioread(ecg_data);
[Elec_1, Fs] = audioread(extref_data);
[Piezo_1, Fs] = audioread(intref_data);

% Choice of step size
step_size = 0.003;

% Normalised LMS implementation
NLMS = dsp.LMSFilter('Method','Normalized LMS','Length',2048,'StepSize',step_size);

% Low pass filter applied to Piezo sensor
LPF = dsp.LowpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',800,...
                        'StopbandFrequency',1000,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);
                    

% Removing DC offset from signals
Main_1 = Main_1(10:end);
Piezo_1 = Piezo_1(10:end);
Elec_1 = Elec_1(10:end);
Main_1 = Main_1 - mean(Main_1);
Piezo_1 = Piezo_1 - mean(Piezo_1);
Elec_1 = Elec_1 - mean(Elec_1);

% Applying algorithm to primary signal with piezo as reference signal
[output, error2, wts] = NLMS(LPF(Piezo_1), Main_1); 

% Plotting results
figure(1);
subplot(3,1,1);
plot(Main_1);
xlabel("Sample number");
ylabel("Amplitude");
title('Original Signal')
ylim([-1 1]);
subplot(3,1,2);
plot(output);
xlabel("Sample number");
ylabel("Amplitude");
title('Estimated Noise')
ylim([-1 1]);
subplot(3,1,3);
plot(error2);
xlabel("Sample number");
ylabel("Amplitude");
title('Filtered Signal')
ylim([-1 1]);
error2_LPF = LPF(error2);
%soundsc(error2, Fs);



%% RLS HEART NOISE CANCELLATION
% Best performance code for denoising heart sounds
clc; clear all; close all;

% Indicating directory where sound files are being stored
Sound_Folder = dir('C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\H-L-9');
H_L = 'C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\H-L-9';

% Data paths
primary_data = fullfile(H_L, Sound_Folder(3).name);
ecg_data = fullfile(H_L, Sound_Folder(4).name);
extref_data = fullfile(H_L, Sound_Folder(5).name);
intref_data = fullfile(H_L, Sound_Folder(6).name);
% 
[Main_1, Fs] = audioread(primary_data);
[ECG_1, Fs] = audioread(ecg_data);
[Elec_1, Fs] = audioread(extref_data);
[Piezo_1, Fs] = audioread(intref_data);


% Removing offset from signal                 
Main_1 = Main_1(15:end) - mean(Main_1(15:end));
Piezo_1 = Piezo_1(15:end) - mean(Piezo_1(15:end));
Elec_1 = Elec_1(15:end) - mean(Elec_1(15:end));

% Filters for signal output
HPF = dsp.HighpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',35,...
                        'StopbandFrequency',25,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);
 
LPF = dsp.LowpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',250,...
                        'StopbandFrequency',300,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80)

% Filter applied to Piezo
LPF2 = dsp.LowpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',500,...
                        'StopbandFrequency',600,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);

% Running RLS several times to derive optimal weights and output
NIter = 5;
weight_L = 11;
w = zeros(weight_L,1);
for K = 1:NIter
    % Removing environmental noise first
    RLS = dsp.RLSFilter('Length', weight_L, 'Method','Conventional RLS','InitialCoefficients',w);
    [y, e] = RLS(Elec_1,Main_1);
    w = RLS.Coefficients;
end

NIter = 5;
weight_L = 11;
w = zeros(weight_L,1);
for K = 1:NIter
    % Removing internal sounds
    RLS = dsp.RLSFilter('Length', weight_L, 'Method','Conventional RLS','InitialCoefficients',w);
    [y_final, e_final] = RLS(LPF2(Piezo_1),e);
    w = RLS.Coefficients;
end

% Plotting results
figure(1);
subplot(3,1,1);
plot(Main_1);
title('Original Signal')
xlabel("Sample number");
ylabel("Amplitude");
subplot(3,1,2);
plot(y + y_final);
title('Estimated Noise (Both reference sensors)')
xlabel("Sample number");
ylabel("Amplitude");
subplot(3,1,3);
plot(LPF(HPF(e_final)));
%plot(e_final);
title('Filtered Signal')
xlabel("Sample number");
ylabel("Amplitude");

% generating output wav file
audiowrite('Clean Heart Sounds.wav',HPF(LPF(e_final*5)),Fs);
% playing scaled sound
%soundsc(LPF(HPF(e_final)), Fs);

%% RLS TOTAL CANCELLATION
% Best performance code for denoising lung sounds
clc; clear all; close all;

% Indicating directory where sound files are being stored
Sound_Folder = dir('C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\L-H-6');
H_L = 'C:\Users\Ben\Desktop\Folders\Stethoscope\ANC\Sound Files\L-H-6';

% Data paths
primary_data = fullfile(H_L, Sound_Folder(3).name);
ecg_data = fullfile(H_L, Sound_Folder(4).name);
extref_data = fullfile(H_L, Sound_Folder(5).name);
intref_data = fullfile(H_L, Sound_Folder(6).name);

% Reading in audio files
[Main_1, Fs] = audioread(primary_data);
[ECG_1, Fs] = audioread(ecg_data);
[Elec_1, Fs] = audioread(extref_data);
[Piezo_1, Fs] = audioread(intref_data);

% Removing offset from signal
Main_1 = Main_1(10:end) - mean(Main_1(10:end));
Piezo_1 = Piezo_1(10:end) - mean(Piezo_1(10:end));
Elec_1 = Elec_1(10:end) - mean(Elec_1(10:end));

% Low pass filter for piezo
LPF = dsp.LowpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',500,...
                        'StopbandFrequency',600,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);

% Filters for signal
LPF_2 = dsp.LowpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',900,...
                        'StopbandFrequency',1000,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);                   
                    
HPF = dsp.HighpassFilter('SampleRate',Fs,...
                        'FilterType','IIR',...
                        'PassbandFrequency',100,...
                        'StopbandFrequency',80,...
                        'PassbandRipple',0.1,...
                        'StopbandAttenuation',80);
                    
% Removing external noise first
NIter = 5;
weight_L = 50;
w = zeros(weight_L,1);
for K = 1:NIter
    RLS = dsp.RLSFilter('Length', weight_L, 'Method','Conventional RLS','InitialCoefficients',w);
    [y, e] = RLS(Elec_1,Main_1);
    w = RLS.Coefficients;
end

% Removing heart sounds next
NIter = 5;
weight_L = 50;
w = zeros(weight_L,1);
Piezo_LPF = LPF(Piezo_1);
for K = 1:NIter
    RLS = dsp.RLSFilter('Length', weight_L, 'Method','Conventional RLS','InitialCoefficients',w);
    [y_final, e_final] = RLS(Piezo_LPF,e);
    w = RLS.Coefficients;
end

% Plotting results
figure(1);
subplot(3,1,1);
plot(Main_1);
title('Original Signal')
xlabel("Sample number");
ylabel("Amplitude");
subplot(3,1,2);
plot(y_final + y);
title('Estimated Noise (Both reference sensors)')
xlabel("Sample number");
ylabel("Amplitude");
subplot(3,1,3);
plot(HPF(LPF_2(e_final)));
%plot(e_final);
title('Filtered Signal')
xlabel("Sample number");
ylabel("Amplitude");
ylim([-0.2 0.2]);

% generating output wav file
audiowrite('Clean Lung Sounds.wav',HPF(LPF_2(e_final*10)),Fs);
% playing scaled output sound
% soundsc(HPF(LPF_2(e_final)),Fs);

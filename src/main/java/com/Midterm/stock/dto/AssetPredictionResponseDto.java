package com.Midterm.stock.dto;

// FastAPI 응답 매핑용
public class AssetPredictionResponseDto {

    private boolean success;

    private int model_prediction;
    private String model_prediction_label;
    private double model_probability;

    private int prediction;
    private String prediction_label;
    private String tone_title;

    private InputSummary input_summary;
    private Analysis analysis;

    public static class InputSummary {
        private long current_asset;
        private long monthly_income;
        private long monthly_expense;
        private long monthly_saving;
        private long goal_amount;
        private int goal_months;
        private double expected_return;
        private int age;
        private String job_type;
        private String risk_preference;

        public long getCurrent_asset() {
            return current_asset;
        }

        public void setCurrent_asset(long current_asset) {
            this.current_asset = current_asset;
        }

        public long getMonthly_income() {
            return monthly_income;
        }

        public void setMonthly_income(long monthly_income) {
            this.monthly_income = monthly_income;
        }

        public long getMonthly_expense() {
            return monthly_expense;
        }

        public void setMonthly_expense(long monthly_expense) {
            this.monthly_expense = monthly_expense;
        }

        public long getMonthly_saving() {
            return monthly_saving;
        }

        public void setMonthly_saving(long monthly_saving) {
            this.monthly_saving = monthly_saving;
        }

        public long getGoal_amount() {
            return goal_amount;
        }

        public void setGoal_amount(long goal_amount) {
            this.goal_amount = goal_amount;
        }

        public int getGoal_months() {
            return goal_months;
        }

        public void setGoal_months(int goal_months) {
            this.goal_months = goal_months;
        }

        public double getExpected_return() {
            return expected_return;
        }

        public void setExpected_return(double expected_return) {
            this.expected_return = expected_return;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getJob_type() {
            return job_type;
        }

        public void setJob_type(String job_type) {
            this.job_type = job_type;
        }

        public String getRisk_preference() {
            return risk_preference;
        }

        public void setRisk_preference(String risk_preference) {
            this.risk_preference = risk_preference;
        }
    }

    public static class Analysis {
        private long required_monthly_saving;
        private long estimated_final_asset;
        private long goal_gap;
        private String message;

        public long getRequired_monthly_saving() {
            return required_monthly_saving;
        }

        public void setRequired_monthly_saving(long required_monthly_saving) {
            this.required_monthly_saving = required_monthly_saving;
        }

        public long getEstimated_final_asset() {
            return estimated_final_asset;
        }

        public void setEstimated_final_asset(long estimated_final_asset) {
            this.estimated_final_asset = estimated_final_asset;
        }

        public long getGoal_gap() {
            return goal_gap;
        }

        public void setGoal_gap(long goal_gap) {
            this.goal_gap = goal_gap;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getModel_prediction() {
        return model_prediction;
    }

    public void setModel_prediction(int model_prediction) {
        this.model_prediction = model_prediction;
    }

    public String getModel_prediction_label() {
        return model_prediction_label;
    }

    public void setModel_prediction_label(String model_prediction_label) {
        this.model_prediction_label = model_prediction_label;
    }

    public double getModel_probability() {
        return model_probability;
    }

    public void setModel_probability(double model_probability) {
        this.model_probability = model_probability;
    }

    public int getPrediction() {
        return prediction;
    }

    public void setPrediction(int prediction) {
        this.prediction = prediction;
    }

    public String getPrediction_label() {
        return prediction_label;
    }

    public void setPrediction_label(String prediction_label) {
        this.prediction_label = prediction_label;
    }

    public String getTone_title() {
        return tone_title;
    }

    public void setTone_title(String tone_title) {
        this.tone_title = tone_title;
    }

    public InputSummary getInput_summary() {
        return input_summary;
    }

    public void setInput_summary(InputSummary input_summary) {
        this.input_summary = input_summary;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }
}
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Projects table
CREATE TABLE projects (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    repository_url TEXT,
    import_type VARCHAR(50) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    analyzed_at TIMESTAMP,
    fingerprint JSONB,
    lines_of_code BIGINT DEFAULT 0,
    file_count INTEGER DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);

-- Modules table
CREATE TABLE modules (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    metadata JSONB
);

CREATE INDEX idx_modules_project_id ON modules(project_id);

-- Dependencies table
CREATE TABLE dependencies (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    version VARCHAR(100),
    type VARCHAR(50) NOT NULL,
    scope VARCHAR(50)
);

CREATE INDEX idx_dependencies_project_id ON dependencies(project_id);

-- Code symbols table
CREATE TABLE code_symbols (
    id VARCHAR(255) PRIMARY KEY,
    module_id VARCHAR(255) NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    fqn VARCHAR(1000) NOT NULL,
    symbol_type VARCHAR(50) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER,
    signature TEXT,
    metadata JSONB
);

CREATE INDEX idx_code_symbols_module_id ON code_symbols(module_id);
CREATE INDEX idx_code_symbols_fqn ON code_symbols(fqn);
CREATE INDEX idx_code_symbols_symbol_type ON code_symbols(symbol_type);

-- Code relations table (graph edges)
CREATE TABLE code_relations (
    id VARCHAR(255) PRIMARY KEY,
    source_symbol_id VARCHAR(255) NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    target_symbol_id VARCHAR(255) NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    relation_type VARCHAR(50) NOT NULL,
    metadata JSONB
);

CREATE INDEX idx_code_relations_source ON code_relations(source_symbol_id);
CREATE INDEX idx_code_relations_target ON code_relations(target_symbol_id);
CREATE INDEX idx_code_relations_type ON code_relations(relation_type);

-- Evidences table
CREATE TABLE evidences (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    assertion_type VARCHAR(100) NOT NULL,
    assertion_key VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER,
    symbol VARCHAR(500),
    snippet TEXT,
    context TEXT
);

CREATE INDEX idx_evidences_project_id ON evidences(project_id);
CREATE INDEX idx_evidences_assertion_type ON evidences(project_id, assertion_type);
CREATE INDEX idx_evidences_assertion_key ON evidences(project_id, assertion_key);

-- Documentation jobs table
CREATE TABLE documentation_jobs (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    output_path VARCHAR(1000),
    error_message TEXT
);

CREATE INDEX idx_documentation_jobs_project_id ON documentation_jobs(project_id);
CREATE INDEX idx_documentation_jobs_status ON documentation_jobs(status);

-- Optional: Embeddings table for semantic search
CREATE TABLE code_embeddings (
    id VARCHAR(255) PRIMARY KEY,
    symbol_id VARCHAR(255) NOT NULL REFERENCES code_symbols(id) ON DELETE CASCADE,
    embedding vector(768), -- dimension depends on the embedding model
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_code_embeddings_symbol_id ON code_embeddings(symbol_id);
-- Vector similarity index (using HNSW for fast approximate nearest neighbor search)
CREATE INDEX idx_code_embeddings_vector ON code_embeddings USING hnsw (embedding vector_cosine_ops);

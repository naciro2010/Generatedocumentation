const API_BASE = '/v1';

// Load projects on page load
document.addEventListener('DOMContentLoaded', () => {
    loadProjects();

    document.getElementById('import-form').addEventListener('submit', handleImport);
});

async function handleImport(e) {
    e.preventDefault();

    const name = document.getElementById('project-name').value;
    const gitUrl = document.getElementById('git-url').value;

    if (!name || !gitUrl) {
        showAlert('Please fill in all fields', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/projects/import`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ name, gitUrl })
        });

        if (!response.ok) {
            throw new Error('Import failed');
        }

        const data = await response.json();
        showAlert(`Project imported successfully! ID: ${data.projectId}`, 'success');

        // Reset form
        document.getElementById('import-form').reset();

        // Reload projects
        setTimeout(() => loadProjects(), 1000);

    } catch (error) {
        showAlert(`Error: ${error.message}`, 'error');
    }
}

async function loadProjects() {
    const container = document.getElementById('projects-list');

    try {
        const response = await fetch(`${API_BASE}/projects`);
        const projects = await response.json();

        if (projects.length === 0) {
            container.innerHTML = '<p class="loading">No projects yet. Import one to get started!</p>';
            return;
        }

        container.innerHTML = projects.map(project => `
            <div class="project-card">
                <div class="project-name">${project.name}</div>
                <span class="project-status status-${project.status.toLowerCase()}">${project.status}</span>
                <div class="project-meta">
                    <div>ID: ${project.id}</div>
                    <div>Created: ${new Date(project.createdAt).toLocaleString()}</div>
                </div>
                <div class="project-actions">
                    <button class="btn-secondary" onclick="viewStatus('${project.id}')">View Status</button>
                    ${project.status === 'ANALYZED' || project.status === 'COMPLETED' ?
                        `<button class="btn-secondary" onclick="generateDocs('${project.id}')">Generate Docs</button>` : ''}
                    ${project.status === 'COMPLETED' ?
                        `<button class="btn-secondary" onclick="exportPDF('${project.id}')">Export PDF</button>` : ''}
                </div>
            </div>
        `).join('');

    } catch (error) {
        container.innerHTML = '<p class="loading">Error loading projects</p>';
    }
}

async function viewStatus(projectId) {
    try {
        const response = await fetch(`${API_BASE}/projects/${projectId}/status`);
        const status = await response.json();

        alert(`
Project: ${status.projectId}
Status: ${status.status}
Progress: ${status.progress}%
Message: ${status.message}
${status.errorMessage ? `\nError: ${status.errorMessage}` : ''}
        `.trim());

    } catch (error) {
        showAlert(`Error: ${error.message}`, 'error');
    }
}

async function generateDocs(projectId) {
    try {
        const response = await fetch(`${API_BASE}/projects/${projectId}/generate-docs`, {
            method: 'POST'
        });

        const data = await response.json();
        showAlert(`Documentation generation started! Job ID: ${data.jobId}`, 'success');

        setTimeout(() => loadProjects(), 2000);

    } catch (error) {
        showAlert(`Error: ${error.message}`, 'error');
    }
}

function exportPDF(projectId) {
    window.open(`/v1/export/${projectId}/pdf`, '_blank');
}

function showAlert(message, type) {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type}`;
    alertDiv.textContent = message;

    const container = document.querySelector('.container');
    container.insertBefore(alertDiv, container.firstChild);

    setTimeout(() => alertDiv.remove(), 5000);
}

// Auto-refresh projects every 10 seconds
setInterval(loadProjects, 10000);
